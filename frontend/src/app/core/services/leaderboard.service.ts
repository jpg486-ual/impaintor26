import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from './game-history.service';

export interface LeaderboardEntry {
  id: number;
  username: string;
  elo: number;
  gamesPlayed?: number;
  gamesWon?: number;
}

@Injectable({
  providedIn: 'root',
})
export class LeaderboardService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/users/leaderboard';

  getLeaderboard(page = 0, size = 50): Observable<PageResponse<LeaderboardEntry>> {
    return this.http.get<PageResponse<LeaderboardEntry>>(this.baseUrl, {
      params: {
        page: String(page),
        size: String(size),
      },
    });
  }
}
