import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export interface RoomConfig {
  drawingTime: number;
  impostorLives: number;
}

export interface CreateRoomResponse {
  roomCode: string;
}

@Injectable({
  providedIn: 'root'
})
export class RoomService {
  private http = inject(HttpClient);
  // Using direct path assuming there is an API proxy setup (e.g. proxy.conf.json or nginx)
  private apiUrl = '/api/rooms';

  private authService = inject(AuthService);

  createRoom(config: RoomConfig): Observable<CreateRoomResponse> {
    return this.http.post<CreateRoomResponse>(`${this.apiUrl}/create`, config);
  }

  joinRoom(roomCode: string): Observable<void> {
    const user = this.authService.getCurrentUser();
    return this.http.post<void>(`${this.apiUrl}/${roomCode}/join`, user || {});
  }

  leaveRoom(roomCode: string): Observable<void> {
    const user = this.authService.getCurrentUser();
    return this.http.post<void>(`${this.apiUrl}/${roomCode}/leave`, user || {});
  }
}
