import { Injectable, signal, computed } from '@angular/core';
import { Point, DrawingStroke } from '../../game/models/drawing';

export interface Player {
  id: number;
  username: string;
  isCurrentUser: boolean;
  isDrawingNow: boolean;
}

export type GameRole = 'painter' | 'impostor';

@Injectable({
  providedIn: 'root'
})
export class GameService {
  // Estado reactivo de la partida
  readonly role = signal<GameRole | null>(null);
  readonly players = signal<Player[]>([]);
  readonly timeLeft = signal<number>(0);
  readonly round = signal<number>(0);
  readonly secretWord = signal<string>('');
  readonly hint = signal<string>('');
  readonly impostorLives = signal<number>(0);

  // Estados calculados
  readonly isImpostor = computed(() => this.role() === 'impostor');
  readonly isMyTurn = computed(() => 
    this.players().find(p => p.isCurrentUser)?.isDrawingNow ?? false
  );

  readonly drawingPlayerName = computed(() => 
    this.players().find(p => p.isDrawingNow)?.username?.toUpperCase() ?? ''
  );

  constructor() {}

  /**
   * Inicializa la partida con datos del servidor (vía WebSocket).
   * Por ahora dejamos el esqueleto listo para el cableado STOMP.
   */
  setInitialState(data: any): void {
    this.role.set(data.role);
    this.players.set(data.players);
    this.round.set(data.round);
    this.timeLeft.set(data.timeLeft);
    if (data.role === 'painter') {
      this.secretWord.set(data.word);
    } else {
      this.hint.set(data.hint);
      this.impostorLives.set(data.lives);
    }
  }

  updateTime(time: number): void {
    this.timeLeft.set(time);
  }

  updatePlayers(players: Player[]): void {
    this.players.set(players);
  }

  submitGuess(word: string): void {
    console.log('Enviando adivinación al servidor:', word);
    // TODO: Implementar envío vía STOMP
  }
}
