import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { WebSocketService } from '../../../core/services/websocket.service';
import { Subscription } from 'rxjs';
import { GameBackgroundComponent } from '../../../shared/components/game-background/game-background.component';
import { RoomConfig } from '../../../core/services/room.service';

export interface Player {
  username: string;
  isHost: boolean;
  avatarUrl?: string;
}

@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [CommonModule, RouterLink, GameBackgroundComponent],
  templateUrl: './lobby.html',
  styleUrl: './lobby.css',
})
export class Lobby implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly wsService = inject(WebSocketService);
  
  roomCode = this.route.snapshot.paramMap.get('code') || 'UNKNOWN';
  Math = Math;
  
  players = signal<Player[]>([]);
  roomConfig = signal<RoomConfig | null>(null);
  isHost = signal<boolean>(false);
  
  private wsSubscription?: Subscription;
  private gameStartSub?: Subscription;

  ngOnInit() {
    this.wsService.connect();
    
    // Suscribirse a los cambios en el lobby
    this.wsSubscription = this.wsService.subscribe(`/topic/room.${this.roomCode}.lobby`).subscribe({
      next: (msg) => {
        try {
          const data = JSON.parse(msg.body);
          if (data.players) this.players.set(data.players);
          if (data.config) this.roomConfig.set(data.config);
          
          const currentUsername = localStorage.getItem('username') || 'Jugador Local';
          const hostPlayer = this.players().find(p => p.isHost);
          if (hostPlayer && hostPlayer.username === currentUsername) {
            this.isHost.set(true);
          }
        } catch(e) {
          console.error("Error parsing lobby update", e);
        }
      }
    });

    // Suscribirse a eventos de juego para detectar el "START"
    this.gameStartSub = this.wsService.subscribe(`/topic/room.${this.roomCode}.game`).subscribe({
      next: (msg) => {
         try {
           const data = JSON.parse(msg.body);
           if (data.type === 'GAME_START') {
             this.router.navigate(['/room', this.roomCode, 'game']);
           }
         } catch(e) {}
      }
    });

  }

  startGame() {
    if (this.isHost()) {
      this.wsService.publish(`/app/room.${this.roomCode}.start`, {});
    }
  }

  copyCode() {
    navigator.clipboard.writeText(this.roomCode);
    alert('¡Código copiado al portapapeles!');
  }

  ngOnDestroy() {
    if (this.wsSubscription) this.wsSubscription.unsubscribe();
    if (this.gameStartSub) this.gameStartSub.unsubscribe();
  }
}
/* hola */
