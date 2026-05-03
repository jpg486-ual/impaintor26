import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { CanvasComponent } from '../../game/components/canvas/canvas';
import { GameService } from '../services/game.service';

/**
 * GameComponent — Vista principal de la partida (fase de dibujo).
 * 
 * SRP: Gestiona la UI de la fase de juego y delega el estado al GameService.
 * Cumple SOLID al desacoplar la lógica de estado (Service) de la vista (Component).
 */
@Component({
  selector: 'app-game',
  standalone: true,
  imports: [CommonModule, FormsModule, CanvasComponent],
  templateUrl: './game.html',
  styleUrl: './game.css',
})
export class Game implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly gameService = inject(GameService);

  // ── Parámetros de ruta ──────────────────────────────────────────────────────
  roomCode = this.route.snapshot.paramMap.get('code') || 'UNKNOWN';

  // ── Estado del juego (Delegado al Servicio) ──────────────────────────────────
  readonly role = this.gameService.role;
  readonly secretWord = this.gameService.secretWord;
  readonly hint = this.gameService.hint;
  readonly impostorLives = this.gameService.impostorLives;
  readonly timeLeft = this.gameService.timeLeft;
  readonly round = this.gameService.round;
  readonly players = this.gameService.players;
  readonly isMyTurn = this.gameService.isMyTurn;
  readonly isImpostor = this.gameService.isImpostor;
  readonly drawingPlayerName = this.gameService.drawingPlayerName;

  /** Texto del intento de adivinación del impostor. */
  guessInput = '';

  // ── Ciclo de vida ───────────────────────────────────────────────────────────
  ngOnInit(): void {
    // Aquí se conectarían las suscripciones WebSocket en producción
  }

  ngOnDestroy(): void {
    // Limpieza de suscripciones
  }

  // ── Métodos públicos ─────────────────────────────────────────────────────────
  /** Formatea los segundos en mm:ss para el template. */
  formatTime(secs: number): string {
    const m = Math.floor(secs / 60).toString().padStart(2, '0');
    const s = (secs % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }

  /** El impostor envía un intento de adivinación (CLAUDE.md §2.4). */
  onSubmitGuess(): void {
    if (!this.guessInput.trim()) return;
    this.gameService.submitGuess(this.guessInput.trim());
    this.guessInput = '';
  }
}
