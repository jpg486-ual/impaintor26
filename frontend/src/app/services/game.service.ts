import {Injectable, signal, computed, OnDestroy} from '@angular/core';
import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {Router} from '@angular/router';
import {Client, IMessage} from '@stomp/stompjs';
import SockJS from 'sockjs-client';

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
  private readonly apiBase = 'http://localhost:8080/api/rooms';
  private readonly wsBase = 'http://localhost:8080/api/ws';
  private stompClient: Client | null = null;
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private countdownHandle: ReturnType<typeof setInterval> | null = null;

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
    return s.gameMode === 'TURN_BASED' ? s.phase === 'DRAWING' : s.phase === 'VOTING';
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
        this.connectWebSocket();
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
        this.connectWebSocket();
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
      error: (e) => this.handleError(e),
    });
  }

  /* ── WebSocket ─────────────────────────── */
  private connectWebSocket(): void {
    const rc = this.roomCode();
    const pid = this.playerId();
    if (!rc || !pid) return;

    this.disconnectWebSocket();

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(this.wsBase) as any,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.wsConnected.set(true);
        this.stompClient?.subscribe(`/topic/room/${rc}/player/${pid}`, (msg: IMessage) => {
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
      onDisconnect: () => this.wsConnected.set(false),
      onStompError: () => this.wsConnected.set(false),
    });

    this.stompClient.activate();
  }

  private disconnectWebSocket(): void {
    if (this.stompClient) {
      this.stompClient.deactivate();
      this.stompClient = null;
    }
    this.wsConnected.set(false);
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

  private handleError(error: HttpErrorResponse): void {
    const apiError = error.error as any;
    const msg = apiError?.message ?? 'Error inesperado.';
    const debug = apiError?.details?.exceptionType
      ? ` [${apiError.details.exceptionType}]`
      : '';
    this.showError(`${msg}${debug}`);
  }
}
