import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface GameHistoryEntry {
  gameId: number;
  playedAt: string;
  role: string;
  winner: boolean;
  eloChange: number | null;
  roomCode: string;
}

export interface PageResponse<T> {
  content: T[];
  pageable: unknown;
  last: boolean;
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  sort: unknown;
  numberOfElements: number;
  first: boolean;
  empty: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class GameHistoryService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/games';

  getHistory(page = 0, size = 50): Observable<PageResponse<GameHistoryEntry>> {
    return this.http.get<PageResponse<GameHistoryEntry>>(this.baseUrl, {
      params: {
        page: String(page),
        size: String(size),
      },
    });
  }
}