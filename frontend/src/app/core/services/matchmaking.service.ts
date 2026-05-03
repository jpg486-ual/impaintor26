import { Injectable, signal, computed, OnDestroy } from '@angular/core';

/**
 * MatchmakingStatus — Representa el estado actual de la búsqueda.
 */
export type MatchmakingStatus = 'idle' | 'searching' | 'found';

/**
 * MatchmakingService — Responsabilidad Única (SRP): gestiona el estado de la cola
 * de matchmaking y la lógica de expansión de rango de ELO.
 *
 * ISO 25010 — Fiabilidad: gestión centralizada del tiempo y rangos.
 */
@Injectable({
  providedIn: 'root'
})
export class MatchmakingService implements OnDestroy {
  // Estado reactivo
  private readonly _status = signal<MatchmakingStatus>('idle');
  private readonly _elapsedSeconds = signal<number>(0);
  private readonly _initialElo = signal<number>(1000);

  private readonly _roomCode = signal<string | null>(null);

  // Señales públicas
  readonly status = this._status.asReadonly();
  readonly elapsedSeconds = this._elapsedSeconds.asReadonly();
  readonly roomCode = this._roomCode.asReadonly();

  // Rango de ELO calculado (CLAUDE.md §3J.2: ±100 cada 10 segundos)
  readonly eloRange = computed(() => {
    const expansion = Math.floor(this._elapsedSeconds() / 10) * 100;
    const base = this._initialElo();
    return {
      min: Math.max(0, base - expansion),
      max: base + expansion
    };
  });

  private timerInterval?: any;

  /**
   * Inicia la búsqueda de partida.
   * En producción, aquí se realizaría la llamada al backend (POST /api/matchmaking/join).
   */
  startSearch(userElo: number = 1000): void {
    this._initialElo.set(userElo);
    this._elapsedSeconds.set(0);
    this._roomCode.set(null);
    this._status.set('searching');

    this.timerInterval = setInterval(() => {
      this._elapsedSeconds.update(s => s + 1);
      // Eliminada la simulación de "partida encontrada" automática.
      // El estado cambiará a 'found' únicamente cuando el servidor emita el evento correspondiente.
    }, 1000);
  }

  /** Cancela la búsqueda actual. */
  cancelSearch(): void {
    this.stopTimer();
    this._status.set('idle');
    this._roomCode.set(null);
  }

  /**
   * Procesa el evento de partida encontrada desde el servidor.
   * Invocado por el manejador de WebSockets (futura implementación).
   */
  onMatchFound(code: string): void {
    this.stopTimer();
    this._roomCode.set(code);
    this._status.set('found');
  }

  private stopTimer(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = undefined;
    }
  }

  ngOnDestroy(): void {
    this.stopTimer();
  }
}
