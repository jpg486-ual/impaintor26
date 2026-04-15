import {Injectable, signal, computed} from '@angular/core';
import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {Router} from '@angular/router';
import {Client, IMessage} from '@stomp/stompjs';

/* ── Interfaces ────────────────────────────── */
export interface RoomJoinResponse {
  roomCode: string;
  playerId: number;
  host: boolean;
}

export type GameMode = 'SIMULTANEOUS' | 'TURN_BASED';
export type GamePhase = 'WAITING' | 'DRAWING' | 'VOTING' | 'ROUND_RESULT' | 'FINISHED';

export interface StrokePoint {
  x: number;
  y: number;
}

export interface PlayerView {
  id: number;
  name: string;
  score: number;
}

export interface StrokeView {
  id: number;
  playerId: number;
  points: StrokePoint[];
}

export interface GameState {
  roomCode: string;
  gameMode: GameMode;
  phase: GamePhase;
  currentRound: number;
  maxRounds: number;
  phaseEndsAt: string | null;
  players: PlayerView[];
  strokes: StrokeView[];
  yourPlayerId: number;
  yourWord: string | null;
  youAreImpostor: boolean;
  youAreHost: boolean;
  impostorRevealedPlayerId: number | null;
  activeDrawerPlayerId: number | null;
  majorityVotedPlayerId: number | null;
  yourVoteTargetPlayerId: number | null;
  resultMessage: string | null;
  totalVotesThisRound: number;
  totalPlayers: number;
}

export interface ThemeOption {
  key: string;
  label: string;
  emoji: string;
  selected: boolean;
}

/* ── Service ───────────────────────────────── */
@Injectable({providedIn: 'root'})
export class GameService {
  private readonly apiBase = '/api/rooms';
  private readonly wsBase = '/api/ws';
  private readonly wsNativeBase = this.buildNativeWsUrl();
  private stompClient: Client | null = null;
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private countdownHandle: ReturnType<typeof setInterval> | null = null;
  private wsFailedAttempts = 0;
  private wsFallbackAttempted = false;
  private manualWsDisconnect = false;
  private wsRoomCode: string | null = null;
  private wsPlayerId: number | null = null;

  /* ── Signals (reactive state) ──────────── */
  readonly roomCode = signal<string | null>(null);
  readonly playerId = signal<number | null>(null);
  readonly isHost = signal(false);
  readonly state = signal<GameState | null>(null);
  readonly secondsLeft = signal(0);
  readonly error = signal('');
  readonly message = signal('');
  readonly isLoading = signal(false);
  readonly wsConnected = signal(false);
  readonly wsFallbackMode = signal(false);
  readonly wsEverConnected = signal(false);
  readonly wsTransport = signal<'NATIVE_WS' | 'SOCKJS' | 'POLLING'>('NATIVE_WS');
  readonly wsLastError = signal('');

  /* ── Computed ───────────────────────────── */
  readonly canStart = computed(() => {
    const s = this.state();
    return !!s && s.youAreHost && s.phase === 'WAITING' && s.players.length >= 3;
  });

  readonly leaderboard = computed(() => {
    const players = this.state()?.players ?? [];
    return [...players].sort((a, b) => b.score - a.score || a.name.localeCompare(b.name));
  });

  readonly canVoteNow = computed(() => {
    const s = this.state();
    if (!s || s.yourVoteTargetPlayerId) return false;
    return s.phase === 'VOTING'
      || (s.gameMode === 'TURN_BASED' && s.phase === 'DRAWING');
  });

  readonly canHostSkipVoting = computed(() => {
    const s = this.state();
    return !!s
      && s.gameMode === 'TURN_BASED'
      && s.youAreHost
      && (s.phase === 'DRAWING' || s.phase === 'VOTING');
  });

  readonly isTurnBased = computed(() => this.state()?.gameMode === 'TURN_BASED');

  readonly gridPlayers = computed(() => {
    const players = this.state()?.players ?? [];
    const padded: Array<PlayerView | null> = [...players];
    while (padded.length < 6) padded.push(null);
    return padded;
  });

  readonly activeDrawerName = computed(() => {
    const s = this.state();
    if (!s?.activeDrawerPlayerId) return '—';
    return s.players.find(p => p.id === s.activeDrawerPlayerId)?.name ?? '—';
  });

  readonly revealedImpostorName = computed(() => {
    const s = this.state();
    if (!s?.impostorRevealedPlayerId) return 'N/A';
    return s.players.find(p => p.id === s.impostorRevealedPlayerId)?.name ?? 'N/A';
  });

  readonly majorityVotedName = computed(() => {
    const s = this.state();
    if (!s?.majorityVotedPlayerId) return 'Empate';
    return s.players.find(p => p.id === s.majorityVotedPlayerId)?.name ?? 'Empate';
  });

  readonly wsStatusLabel = computed(() => {
    if (this.wsConnected()) {
      return this.wsTransport() === 'SOCKJS' ? 'Conectado (SockJS)' : 'Conectado (WebSocket)';
    }
    if (this.wsFallbackMode()) return 'Modo respaldo (polling)';
    return 'Reconectando...';
  });

  readonly themes: ThemeOption[] = [
    {key: 'animales', label: 'Animales', emoji: '🐾', selected: true},
    {key: 'comida', label: 'Comida', emoji: '🍕', selected: true},
    {key: 'deportes', label: 'Deportes', emoji: '⚽', selected: false},
    {key: 'objetos', label: 'Objetos', emoji: '💡', selected: false},
    {key: 'profesiones', label: 'Profesiones', emoji: '👨‍🍳', selected: false},
  ];

  constructor(private http: HttpClient, private router: Router) {}

  /* ── Room actions ──────────────────────── */
  createRoom(username: string, gameMode: GameMode, roundDuration: number, votingDuration: number, maxRounds: number): void {
    this.clearAlerts();
    if (!username.trim()) {
      this.showError('Introduce un nombre de usuario.');
      return;
    }

    const selectedThemes = this.themes.filter(t => t.selected).map(t => t.key);
    if (selectedThemes.length === 0) {
      this.showError('Selecciona al menos un tema.');
      return;
    }

    this.isLoading.set(true);
    this.http.post<RoomJoinResponse>(this.apiBase, {
      username: username.trim(),
      roundDurationSeconds: roundDuration,
      votingDurationSeconds: votingDuration,
      maxRounds,
      themes: selectedThemes,
      gameMode,
    }).subscribe({
      next: (res) => {
        this.roomCode.set(res.roomCode);
        this.playerId.set(res.playerId);
        this.isHost.set(res.host);
        this.isLoading.set(false);
        this.showMessage(`Sala ${res.roomCode} creada`);
        void this.connectWebSocket();
        this.startPolling();
        this.router.navigate(['/room', res.roomCode]);
      },
      error: (e) => { this.isLoading.set(false); this.handleError(e); },
    });
  }

  joinRoom(username: string, code: string): void {
    this.clearAlerts();
    if (!username.trim() || !code.trim()) {
      this.showError('Rellena nombre y código de sala.');
      return;
    }

    const roomCode = code.trim().toUpperCase();
    this.isLoading.set(true);
    this.http.post<RoomJoinResponse>(`${this.apiBase}/${roomCode}/join`, {
      username: username.trim(),
    }).subscribe({
      next: (res) => {
        this.roomCode.set(res.roomCode);
        this.playerId.set(res.playerId);
        this.isHost.set(res.host);
        this.isLoading.set(false);
        this.showMessage(`Unido a sala ${res.roomCode}`);
        void this.connectWebSocket();
        this.startPolling();
        this.router.navigate(['/room', res.roomCode]);
      },
      error: (e) => { this.isLoading.set(false); this.handleError(e); },
    });
  }

  startGame(): void {
    const rc = this.roomCode();
    const pid = this.playerId();
    if (!rc || !pid) return;

    this.isLoading.set(true);
    this.http.post<void>(`${this.apiBase}/${rc}/start`, {playerId: pid}).subscribe({
      next: () => { this.isLoading.set(false); this.fetchState(); },
      error: (e) => { this.isLoading.set(false); this.handleError(e); },
    });
  }

  vote(targetPlayerId: number): void {
    const rc = this.roomCode();
    const pid = this.playerId();
    if (!rc || !pid) return;

    this.http.post<void>(`${this.apiBase}/${rc}/votes`, {
      voterPlayerId: pid,
      targetPlayerId,
    }).subscribe({
      next: () => this.fetchState(),
      error: (e) => this.handleError(e),
    });
  }

  skipVoting(): void {
    const rc = this.roomCode();
    const pid = this.playerId();
    if (!rc || !pid) return;

    this.http.post<void>(`${this.apiBase}/${rc}/votes/skip`, {
      playerId: pid,
    }).subscribe({
      next: () => this.fetchState(),
      error: (e) => this.handleError(e),
    });
  }

  sendStroke(points: StrokePoint[]): void {
    const rc = this.roomCode();
    const pid = this.playerId();
    if (!rc || !pid || points.length < 2) return;

    this.http.post<void>(`${this.apiBase}/${rc}/strokes`, {
      playerId: pid,
      points,
    }).subscribe({
      error: (e) => this.handleError(e),
    });
  }

  /* ── State fetching ────────────────────── */
  fetchState(): void {
    const rc = this.roomCode();
    const pid = this.playerId();
    if (!rc || !pid) return;

    this.http.get<GameState>(`${this.apiBase}/${rc}/state`, {
      params: {playerId: pid},
    }).subscribe({
      next: (state) => {
        this.state.set(state);
        this.updateCountdown(state.phaseEndsAt);
      },
      error: (e) => this.handleStateFetchError(e),
    });
  }

  /* ── WebSocket ─────────────────────────── */
  private async connectWebSocket(): Promise<void> {
    const rc = this.roomCode();
    const pid = this.playerId();
    if (!rc || !pid) return;

    this.wsRoomCode = rc;
    this.wsPlayerId = pid;
    this.wsFailedAttempts = 0;
    this.wsFallbackAttempted = false;
    this.wsLastError.set('');
    this.wsFallbackMode.set(false);
    this.wsEverConnected.set(false);

    this.disconnectWebSocket();
    this.manualWsDisconnect = false;

    this.connectWithNativeWebSocket(rc, pid);
  }

  private connectWithNativeWebSocket(roomCode: string, playerId: number): void {
    this.wsTransport.set('NATIVE_WS');
    this.stompClient = this.buildStompClient({
      roomCode,
      playerId,
      brokerURL: this.wsNativeBase,
      transportName: 'WebSocket',
    });
    this.stompClient.activate();
  }

  private async connectWithSockJs(roomCode: string, playerId: number): Promise<void> {
    const sockJsModule = await import('sockjs-client').catch(() => null);
    if (!sockJsModule) {
      this.showError('No se pudo inicializar el canal en tiempo real.');
      this.wsFallbackMode.set(true);
      this.wsTransport.set('POLLING');
      return;
    }
    const SockJSCtor = (sockJsModule as any).default ?? sockJsModule;

    this.wsTransport.set('SOCKJS');
    this.disconnectWebSocket();
    this.manualWsDisconnect = false;
    this.stompClient = this.buildStompClient({
      roomCode,
      playerId,
      webSocketFactory: () => new SockJSCtor(this.wsBase) as any,
      transportName: 'SockJS',
    });

    this.stompClient.activate();
  }

  private buildStompClient(config: {
    roomCode: string;
    playerId: number;
    transportName: 'WebSocket' | 'SockJS';
    brokerURL?: string;
    webSocketFactory?: () => WebSocket;
  }): Client {
    return new Client({
      brokerURL: config.brokerURL,
      webSocketFactory: config.webSocketFactory,
      reconnectDelay: 3000,
      // Spring simple broker doesn't send heartbeats by default; disabling avoids false reconnect loops.
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
      connectionTimeout: 8000,
      debug: (line: string) => {
        console.debug(`[STOMP ${config.transportName}] ${line}`);
      },
      onConnect: () => {
        this.wsConnected.set(true);
        this.wsEverConnected.set(true);
        this.wsFallbackMode.set(false);
        this.wsFailedAttempts = 0;
        this.wsLastError.set('');
        this.stompClient?.subscribe(this.buildPlayerTopic(config.roomCode, config.playerId), (msg: IMessage) => {
          try {
            const state: GameState = JSON.parse(msg.body);
            this.state.set(state);
            this.updateCountdown(state.phaseEndsAt);
          } catch (e) {
            console.error('WS parse error', e);
          }
        });
        // Fetch initial state
        this.fetchState();
      },
      onDisconnect: () => this.markWsFailure('Desconectado del broker STOMP'),
      onStompError: (frame) => this.markWsFailure(`STOMP error: ${frame.headers['message'] ?? 'sin detalle'}`),
      onWebSocketClose: (event) => this.markWsFailure(`Socket cerrado (${event.code})`),
      onWebSocketError: () => this.markWsFailure('Error de transporte WebSocket'),
    });
  }

  private disconnectWebSocket(): void {
    this.manualWsDisconnect = true;
    if (this.stompClient) {
      this.stompClient.deactivate();
      this.stompClient = null;
    }
    this.wsConnected.set(false);
  }

  private markWsFailure(reason: string): void {
    if (this.manualWsDisconnect) {
      return;
    }

    this.wsConnected.set(false);
    this.wsLastError.set(reason);
    console.warn(`[WS] ${reason}`);
    this.wsFailedAttempts += 1;

    if (!this.wsEverConnected()
      && !this.wsFallbackAttempted
      && this.wsTransport() === 'NATIVE_WS'
      && this.wsRoomCode
      && this.wsPlayerId) {
      this.wsFallbackAttempted = true;
      void this.connectWithSockJs(this.wsRoomCode, this.wsPlayerId);
      return;
    }

    if (!this.wsEverConnected() && this.wsFailedAttempts >= 2) {
      this.wsFallbackMode.set(true);
      this.wsTransport.set('POLLING');
    }
  }

  private startPolling(): void {
    this.stopPolling();
    // Fallback polling in case WebSocket drops
    this.pollHandle = setInterval(() => this.fetchState(), 3000);
  }

  private stopPolling(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  /* ── Countdown ─────────────────────────── */
  private updateCountdown(phaseEndsAt: string | null): void {
    if (this.countdownHandle) clearInterval(this.countdownHandle);

    const tick = () => {
      if (!phaseEndsAt) { this.secondsLeft.set(0); return; }
      const diff = new Date(phaseEndsAt).getTime() - Date.now();
      this.secondsLeft.set(Math.max(0, Math.ceil(diff / 1000)));
    };

    tick();
    this.countdownHandle = setInterval(tick, 250);
  }

  /* ── Helpers ───────────────────────────── */
  isDrawingEnabledFor(playerId: number): boolean {
    const s = this.state();
    if (!s || s.phase !== 'DRAWING' || this.playerId() !== playerId) return false;
    if (s.gameMode === 'TURN_BASED') return s.activeDrawerPlayerId === this.playerId();
    return true;
  }

  leaveRoom(): void {
    this.disconnectWebSocket();
    this.stopPolling();
    if (this.countdownHandle) clearInterval(this.countdownHandle);
    this.wsFailedAttempts = 0;
    this.wsFallbackAttempted = false;
    this.wsFallbackMode.set(false);
    this.wsEverConnected.set(false);
    this.wsTransport.set('NATIVE_WS');
    this.wsLastError.set('');
    this.wsRoomCode = null;
    this.wsPlayerId = null;
    this.roomCode.set(null);
    this.playerId.set(null);
    this.isHost.set(false);
    this.state.set(null);
    this.secondsLeft.set(0);
    this.router.navigate(['/']);
  }

  getPlayerColor(playerId: number): string {
    const colors = ['#8b5cf6', '#06b6d4', '#ec4899', '#22c55e', '#f59e0b', '#ef4444'];
    const s = this.state();
    if (!s) return colors[0];
    const idx = s.players.findIndex(p => p.id === playerId);
    return colors[idx % colors.length];
  }

  getPlayerInitial(name: string): string {
    return name.charAt(0).toUpperCase();
  }

  private clearAlerts(): void {
    this.error.set('');
    this.message.set('');
  }

  private showMessage(msg: string): void {
    this.clearAlerts();
    this.message.set(msg);
    setTimeout(() => this.message.set(''), 4000);
  }

  showError(msg: string): void {
    this.clearAlerts();
    this.error.set(msg);
    setTimeout(() => this.error.set(''), 6000);
  }

  private buildNativeWsUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}${this.wsBase}`;
  }

  private buildPlayerTopic(roomCode: string, playerId: number): string {
    return `/topic/room.${roomCode}.player.${playerId}`;
  }

  private handleStateFetchError(error: HttpErrorResponse): void {
    if (error.status === 404 && this.roomCode()) {
      this.showError('La sala ha finalizado o ya no existe.');
      this.leaveRoom();
      return;
    }
    this.handleError(error);
  }

  private handleError(error: HttpErrorResponse): void {
    if (error.status === 0) {
      this.showError('No se pudo conectar con el servidor (red/CORS/proxy).');
      return;
    }

    const apiError = error.error as any;
    const responseText = typeof error.error === 'string' ? error.error.trim() : '';
    const msg = apiError?.message ?? (responseText || error.message || `Error HTTP ${error.status}`);
    const debug = apiError?.details?.exceptionType
      ? ` [${apiError.details.exceptionType}]`
      : error.status ? ` [HTTP ${error.status}]` : '';
    this.showError(`${msg}${debug}`);
  }
}
