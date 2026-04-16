import {Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {GameService, GameMode} from '../../services/game.service';

@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './lobby.component.html',
  styleUrl: './lobby.component.css',
})
export class LobbyComponent {
  readonly game = inject(GameService);

  username = '';
  roomCodeInput = '';
  selectedGameMode: GameMode = 'SIMULTANEOUS';
  drawingDuration = 60;
  roundDuration = 60;
  votingDuration = 25;
  maxRounds = 5;
  activeTab: 'create' | 'join' | 'ranked' = 'create';

  createRoom(): void {
    const roundDurationSeconds = this.selectedGameMode === 'TURN_BASED'
      ? this.drawingDuration
      : this.roundDuration;
    const votingDurationSeconds = this.selectedGameMode === 'TURN_BASED'
      ? 10
      : this.votingDuration;

    this.game.createRoom(
      this.username,
      this.selectedGameMode,
      roundDurationSeconds,
      votingDurationSeconds,
      this.maxRounds,
    );
  }

  joinRoom(): void {
    this.game.joinRoom(this.username, this.roomCodeInput);
  }

  startRankedSearch(): void {
    this.game.startRankedSearch(this.username);
  }

  confirmRankedMatch(): void {
    this.game.confirmRankedMatch();
  }

  leaveRankedQueue(): void {
    this.game.leaveRankedQueue();
  }

  toggleTheme(theme: any): void {
    theme.selected = !theme.selected;
  }
}
