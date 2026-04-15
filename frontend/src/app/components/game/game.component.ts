import {Component, inject, OnInit, computed} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router} from '@angular/router';
import {GameService, StrokePoint, StrokeView, PlayerView} from '../../services/game.service';
import {CanvasComponent} from '../canvas/canvas.component';

@Component({
  selector: 'app-game',
  standalone: true,
  imports: [CommonModule, CanvasComponent],
  templateUrl: './game.component.html',
  styleUrl: './game.component.css',
})
export class GameComponent implements OnInit {
  readonly game = inject(GameService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  showRoleOverlay = false;
  showResultOverlay = false;
  private lastPhase: string | null = null;
  private lastRound = 0;

  readonly strokesByPlayer = computed(() => {
    const state = this.game.state();
    if (!state) return new Map<number, StrokeView[]>();
    const map = new Map<number, StrokeView[]>();
    for (const stroke of state.strokes) {
      const list = map.get(stroke.playerId) ?? [];
      list.push(stroke);
      map.set(stroke.playerId, list);
    }
    return map;
  });

  readonly isLowTime = computed(() => this.game.secondsLeft() <= 10 && this.game.secondsLeft() > 0);
  readonly timerPercent = computed(() => {
    const state = this.game.state();
    if (!state?.phaseEndsAt) return 0;
    const total = state.phase === 'DRAWING'
      ? 1 // unknown total, use raw seconds
      : 1;
    return Math.min(100, Math.max(0, (this.game.secondsLeft() / 60) * 100));
  });

  readonly votingProgress = computed(() => {
    const s = this.game.state();
    if (!s) return '';
    return `${s.totalVotesThisRound}/${s.totalPlayers}`;
  });

  readonly turnCanvasStrokes = computed(() => {
    const state = this.game.state();
    if (!state || state.gameMode !== 'TURN_BASED') return [];
    return state.strokes;
  });

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('code');
    if (!this.game.roomCode() && code) {
      // Landed here directly without going through lobby
      this.router.navigate(['/']);
      return;
    }

    // Watch for phase changes to show overlays
    // Using a simple check approach since we're using signals
    setInterval(() => this.checkPhaseChanges(), 500);
  }

  private checkPhaseChanges(): void {
    const state = this.game.state();
    if (!state) return;

    // Show role overlay at start of drawing phase
    if (state.phase === 'DRAWING' && (this.lastPhase !== 'DRAWING' || this.lastRound !== state.currentRound)) {
      this.showRoleOverlay = true;
      setTimeout(() => this.showRoleOverlay = false, 3500);
    }

    // Show result overlay
    if (state.phase === 'ROUND_RESULT' && this.lastPhase !== 'ROUND_RESULT') {
      this.showResultOverlay = true;
      setTimeout(() => this.showResultOverlay = false, 7000);
    }

    if (state.phase === 'FINISHED' && this.lastPhase !== 'FINISHED') {
      this.showResultOverlay = true;
    }

    this.lastPhase = state.phase;
    this.lastRound = state.currentRound;
  }

  getStrokesForPlayer(playerId: number): StrokeView[] {
    return this.strokesByPlayer().get(playerId) ?? [];
  }

  isDrawingEnabled(playerId: number): boolean {
    return this.game.isDrawingEnabledFor(playerId);
  }

  isTurnCanvasEnabled(): boolean {
    const state = this.game.state();
    if (!state) return false;
    return state.gameMode === 'TURN_BASED'
      && state.phase === 'DRAWING'
      && state.activeDrawerPlayerId === this.game.playerId();
  }

  turnCanvasHint(): string {
    const state = this.game.state();
    if (!state || state.gameMode !== 'TURN_BASED') return '';
    if (state.phase !== 'DRAWING') return 'Canvas bloqueado fuera de la fase de dibujo.';
    if (this.isTurnCanvasEnabled()) return 'Es tu turno: dibuja en el canvas principal.';
    return `Observando turno de ${this.game.activeDrawerName()}.`;
  }

  onStrokeComplete(points: StrokePoint[]): void {
    this.game.sendStroke(points);
  }

  isCurrentPlayer(playerId: number): boolean {
    return playerId === this.game.playerId();
  }

  getPlayerColor(playerId: number): string {
    return this.game.getPlayerColor(playerId);
  }

  getPlayerInitial(name: string): string {
    return this.game.getPlayerInitial(name);
  }

  vote(targetPlayerId: number): void {
    this.game.vote(targetPlayerId);
  }

  canVotePlayer(playerId: number): boolean {
    return this.game.canVoteNow() && !this.isCurrentPlayer(playerId);
  }

  skipVoting(): void {
    this.game.skipVoting();
  }

  isActiveTurn(playerId: number): boolean {
    const state = this.game.state();
    return !!state
      && state.gameMode === 'TURN_BASED'
      && state.phase === 'DRAWING'
      && state.activeDrawerPlayerId === playerId;
  }

  getTurnBadgeText(player: PlayerView): string {
    return this.isCurrentPlayer(player.id)
      ? '✏️ Tu turno: dibuja ahora'
      : `⏳ Turno de ${player.name}`;
  }

  startGame(): void {
    this.game.startGame();
  }

  leaveRoom(): void {
    this.game.leaveRoom();
  }

  trackPlayer(index: number, player: PlayerView | null): number {
    return player?.id ?? -(index + 1);
  }

  trackLeaderboard(index: number, player: PlayerView): number {
    return player.id;
  }
}
