import { Component, inject, OnInit, OnDestroy, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatchmakingService } from '../../core/services/matchmaking.service';
import { UserService } from '../../core/services/user.service';

/**
 * MatchmakingComponent — Pantalla de búsqueda de partida competitiva.
 *
 * SRP: gestiona la UI de la cola de espera y la navegación al cancelar.
 * ISO 25010 — Usabilidad: retroalimentación visual clara del progreso (tiempo y rango).
 */
@Component({
  selector: 'app-matchmaking',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './matchmaking.component.html',
  styleUrl: './matchmaking.component.css'
})
export class MatchmakingComponent implements OnInit, OnDestroy {
  private readonly matchmakingService = inject(MatchmakingService);
  private readonly userService = inject(UserService);
  private readonly router = inject(Router);

  // Exponemos las señales al template
  readonly status = this.matchmakingService.status;
  readonly elapsedSeconds = this.matchmakingService.elapsedSeconds;
  readonly eloRange = this.matchmakingService.eloRange;
  readonly roomCode = this.matchmakingService.roomCode;

  constructor() {
    effect(() => {
      if (this.status() === 'found') {
        const code = this.roomCode();
        if (code) {
          this.router.navigate(['/room', code, 'game']);
        }
      }
    });
  }

  ngOnInit(): void {
    const userElo = this.userService.currentUser()?.elo ?? 1000;
    this.matchmakingService.startSearch(userElo);
  }

  /** Formatea los segundos en mm:ss */
  getFormattedTime(): string {
    const total = this.elapsedSeconds();
    const mins = Math.floor(total / 60);
    const secs = total % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  onCancel(): void {
    this.matchmakingService.cancelSearch();
    this.router.navigate(['/home']);
  }

  ngOnDestroy(): void {
    // Aseguramos que la búsqueda se detiene si el componente se destruye
    if (this.status() === 'searching') {
      this.matchmakingService.cancelSearch();
    }
  }
}
