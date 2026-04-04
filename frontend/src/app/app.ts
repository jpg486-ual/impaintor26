import {AfterViewInit, Component, ElementRef, OnDestroy, QueryList, ViewChildren, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient, HttpErrorResponse} from '@angular/common/http';

interface RoomJoinResponse {
  roomCode: string;
  playerId: number;
  host: boolean;
}

interface StrokePoint {
  x: number;
  y: number;
}

interface PlayerView {
  id: number;
  name: string;
  score: number;
}

interface StrokeView {
  id: number;
  playerId: number;
  points: StrokePoint[];
}

type GamePhase = 'WAITING' | 'DRAWING' | 'VOTING' | 'ROUND_RESULT' | 'FINISHED';

interface GameState {
  roomCode: string;
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
  majorityVotedPlayerId: number | null;
  yourVoteTargetPlayerId: number | null;
  resultMessage: string | null;
}

interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: {
    exceptionType?: string;
    exceptionMessage?: string;
  };
}

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements AfterViewInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly apiBase = 'http://localhost:8080/api/rooms';

  @ViewChildren('playerCanvas')
  canvasRefs!: QueryList<ElementRef<HTMLCanvasElement>>;

  username = '';
  roomCodeInput = '';
  roundDurationSeconds = 60;
  votingDurationSeconds = 25;
  maxRounds = 5;
  themes = [
    {key: 'animales', label: 'Animales', selected: true},
    {key: 'comida', label: 'Comida', selected: true},
    {key: 'deportes', label: 'Deportes', selected: false},
    {key: 'objetos', label: 'Objetos', selected: false},
    {key: 'profesiones', label: 'Profesiones', selected: false},
  ];

  roomCode: string | null = null;
  playerId: number | null = null;
  state: GameState | null = null;
  message = '';
  error = '';
  secondsLeft = 0;

  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private countdownHandle: ReturnType<typeof setInterval> | null = null;
  private alertHandle: ReturnType<typeof setTimeout> | null = null;
  private currentStroke: StrokePoint[] = [];
  private drawingForPlayerId: number | null = null;

  get canStart(): boolean {
    return !!this.state && this.state.youAreHost && this.state.phase === 'WAITING' && this.state.players.length >= 3;
  }

  get leaderboard(): PlayerView[] {
    return [...(this.state?.players ?? [])].sort((a, b) => b.score - a.score || a.name.localeCompare(b.name));
  }

  get revealedImpostorName(): string {
    if (!this.state || this.state.impostorRevealedPlayerId == null) {
      return 'N/A';
    }
    return this.state.players.find((p) => p.id === this.state!.impostorRevealedPlayerId)?.name ?? 'N/A';
  }

  get majorityVotedName(): string {
    if (!this.state || this.state.majorityVotedPlayerId == null) {
      return 'Empate/sin mayoría';
    }
    return this.state.players.find((p) => p.id === this.state!.majorityVotedPlayerId)?.name ?? 'Empate/sin mayoría';
  }

  get gridPlayers(): Array<PlayerView | null> {
    const players = this.state?.players ?? [];
    return [...players, ...Array(Math.max(0, 6 - players.length)).fill(null)];
  }

  ngAfterViewInit(): void {
    this.canvasRefs.changes.subscribe(() => this.redrawCanvases());
  }

  ngOnDestroy(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
    }
    if (this.countdownHandle) {
      clearInterval(this.countdownHandle);
    }
    if (this.alertHandle) {
      clearTimeout(this.alertHandle);
    }
  }

  createRoom(): void {
    this.clearAlerts();
    if (!this.username.trim()) {
      this.showError('Introduce un nombre de usuario.');
      return;
    }

    const selectedThemes = this.themes.filter((t) => t.selected).map((t) => t.key);
    if (selectedThemes.length === 0) {
      this.showError('Selecciona al menos un tema.');
      return;
    }

    this.http.post<RoomJoinResponse>(this.apiBase, {
      username: this.username.trim(),
      roundDurationSeconds: this.roundDurationSeconds,
      votingDurationSeconds: this.votingDurationSeconds,
      maxRounds: this.maxRounds,
      themes: selectedThemes,
    }).subscribe({
      next: (res) => {
        this.roomCode = res.roomCode;
        this.playerId = res.playerId;
        this.showMessage(`Sala ${res.roomCode} creada.`);
        this.fetchState(true);
      },
      error: (e) => this.handleError(e),
    });
  }

  joinRoom(): void {
    this.clearAlerts();
    if (!this.username.trim() || !this.roomCodeInput.trim()) {
      this.showError('Rellena nombre y código de sala.');
      return;
    }

    const code = this.roomCodeInput.trim().toUpperCase();
    this.http.post<RoomJoinResponse>(`${this.apiBase}/${code}/join`, {
      username: this.username.trim(),
    }).subscribe({
      next: (res) => {
        this.roomCode = res.roomCode;
        this.playerId = res.playerId;
        this.showMessage(`Te uniste a la sala ${res.roomCode}.`);
        this.fetchState(true);
      },
      error: (e) => this.handleError(e),
    });
  }

  startGame(): void {
    if (!this.roomCode || !this.playerId) {
      return;
    }
    this.clearAlerts();
    this.http.post<void>(`${this.apiBase}/${this.roomCode}/start`, {playerId: this.playerId}).subscribe({
      next: () => this.fetchState(false),
      error: (e) => this.handleError(e),
    });
  }

  vote(targetPlayerId: number): void {
    if (!this.roomCode || !this.playerId || !this.state || this.state.yourVoteTargetPlayerId) {
      return;
    }

    this.http.post<void>(`${this.apiBase}/${this.roomCode}/votes`, {
      voterPlayerId: this.playerId,
      targetPlayerId,
    }).subscribe({
      next: () => this.fetchState(false),
      error: (e) => this.handleError(e),
    });
  }

  onPointerDown(event: PointerEvent, playerId: number): void {
    if (!this.isDrawingEnabledFor(playerId)) {
      return;
    }

    const canvas = event.target as HTMLCanvasElement;
    canvas.setPointerCapture(event.pointerId);
    this.drawingForPlayerId = playerId;
    this.currentStroke = [this.toPoint(event, canvas)];
  }

  onPointerMove(event: PointerEvent, playerId: number): void {
    if (!this.isDrawingEnabledFor(playerId) || this.drawingForPlayerId !== playerId) {
      return;
    }

    const canvas = event.target as HTMLCanvasElement;
    this.currentStroke.push(this.toPoint(event, canvas));
    this.drawStroke(canvas, this.currentStroke.slice(-2));
  }

  onPointerUp(event: PointerEvent, playerId: number): void {
    if (!this.isDrawingEnabledFor(playerId) || this.drawingForPlayerId !== playerId) {
      return;
    }

    const canvas = event.target as HTMLCanvasElement;
    canvas.releasePointerCapture(event.pointerId);
    this.currentStroke.push(this.toPoint(event, canvas));

    if (this.currentStroke.length > 1 && this.roomCode && this.playerId) {
      const payload = {
        playerId: this.playerId,
        points: this.currentStroke,
      };
      this.http.post<void>(`${this.apiBase}/${this.roomCode}/strokes`, payload).subscribe({
        next: () => this.fetchState(false),
        error: (e) => this.handleError(e),
      });
    }

    this.currentStroke = [];
    this.drawingForPlayerId = null;
  }

  private fetchState(resetPolling: boolean): void {
    if (!this.roomCode || !this.playerId) {
      return;
    }

    this.http.get<GameState>(`${this.apiBase}/${this.roomCode}/state`, {
      params: {playerId: this.playerId},
    }).subscribe({
      next: (state) => {
        this.state = state;
        this.secondsLeft = this.computeSecondsLeft(state.phaseEndsAt);
        this.redrawCanvases();
        this.startCountdownClock();

        if (resetPolling) {
          this.startPolling();
        }
      },
      error: (e) => this.handleError(e),
    });
  }

  private startPolling(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
    }

    this.pollHandle = setInterval(() => {
      this.fetchState(false);
    }, 1500);
  }

  private startCountdownClock(): void {
    if (this.countdownHandle) {
      clearInterval(this.countdownHandle);
    }

    this.countdownHandle = setInterval(() => {
      this.secondsLeft = this.computeSecondsLeft(this.state?.phaseEndsAt ?? null);
    }, 250);
  }

  private computeSecondsLeft(phaseEndsAt: string | null): number {
    if (!phaseEndsAt) {
      return 0;
    }

    const diffMs = new Date(phaseEndsAt).getTime() - Date.now();
    return Math.max(0, Math.ceil(diffMs / 1000));
  }

  private redrawCanvases(): void {
    if (!this.state || !this.canvasRefs) {
      return;
    }

    const strokesByPlayer = new Map<number, StrokeView[]>();
    for (const stroke of this.state.strokes) {
      const list = strokesByPlayer.get(stroke.playerId) ?? [];
      list.push(stroke);
      strokesByPlayer.set(stroke.playerId, list);
    }

    for (const canvasRef of this.canvasRefs.toArray()) {
      const canvas = canvasRef.nativeElement;
      const playerId = Number(canvas.dataset['playerId']);
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        continue;
      }
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = '#fff';
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      const playerStrokes = strokesByPlayer.get(playerId) ?? [];
      for (const stroke of playerStrokes) {
        this.drawStroke(canvas, stroke.points);
      }
    }
  }

  private drawStroke(canvas: HTMLCanvasElement, points: StrokePoint[]): void {
    if (points.length < 2) {
      return;
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }

    ctx.strokeStyle = '#000';
    ctx.lineWidth = 2;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();

    const start = this.scaleToCanvas(points[0], canvas);
    ctx.moveTo(start.x, start.y);

    for (let i = 1; i < points.length; i++) {
      const p = this.scaleToCanvas(points[i], canvas);
      ctx.lineTo(p.x, p.y);
    }

    ctx.stroke();
  }

  private scaleToCanvas(point: StrokePoint, canvas: HTMLCanvasElement): {x: number; y: number} {
    return {
      x: (point.x / 1000) * canvas.width,
      y: (point.y / 1000) * canvas.height,
    };
  }

  private toPoint(event: PointerEvent, canvas: HTMLCanvasElement): StrokePoint {
    const rect = canvas.getBoundingClientRect();
    const x = Math.min(1000, Math.max(0, Math.round(((event.clientX - rect.left) / rect.width) * 1000)));
    const y = Math.min(1000, Math.max(0, Math.round(((event.clientY - rect.top) / rect.height) * 1000)));
    return {x, y};
  }

  private isDrawingEnabledFor(playerId: number): boolean {
    return !!this.state
      && this.state.phase === 'DRAWING'
      && this.playerId === playerId;
  }

  private clearAlerts(): void {
    if (this.alertHandle) {
      clearTimeout(this.alertHandle);
      this.alertHandle = null;
    }
    this.error = '';
    this.message = '';
  }

  private showMessage(message: string): void {
    this.clearAlerts();
    this.message = message;
    this.alertHandle = setTimeout(() => {
      this.message = '';
      this.alertHandle = null;
    }, 3500);
  }

  private showError(message: string): void {
    this.clearAlerts();
    this.error = message;
    this.alertHandle = setTimeout(() => {
      this.error = '';
      this.alertHandle = null;
    }, 6500);
  }

  private handleError(error: HttpErrorResponse): void {
    const apiError = error.error as ApiErrorResponse | undefined;
    const baseMessage = apiError?.message ?? 'Error inesperado.';
    const debugSuffix = apiError?.details?.exceptionType
      ? ` [${apiError.details.exceptionType}: ${apiError.details.exceptionMessage ?? ''}]`
      : '';
    this.showError(`${baseMessage}${debugSuffix}`);
  }
}
