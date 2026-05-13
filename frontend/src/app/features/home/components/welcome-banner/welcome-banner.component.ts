import { Component, inject, computed, Signal } from '@angular/core';
import { UserService } from '../../../../core/services/user.service';
import { User } from '../../../../core/models/user.model';

/**
 * WelcomeBannerComponent — Responsabilidad Única (SRP):
 * muestra únicamente el saludo personalizado al usuario.
 *
 * ISO 25010 — Usabilidad (reconocibilidad): el nombre del jugador aparece
 * inmediatamente en primer plano para reforzar la identidad personal.
 */
@Component({
  selector: 'app-welcome-banner',
  standalone: true,
  template: `
    <header class="welcome-banner" role="banner">
      <div class="banner-emblem" aria-hidden="true">🎨</div>
      <h1 class="welcome-title">
        Bienvenido,
        <span class="player-name">{{ playerName() }}</span>
      </h1>
      <p class="welcome-subtitle">¿Listo para pintar… o engañar?</p>
      <div class="banner-divider" aria-hidden="true"></div>
    </header>
  `,
  styleUrl: './welcome-banner.component.css',
})
export class WelcomeBannerComponent {
  private readonly userService = inject(UserService);

  /**
   * Señal computada que deriva el nombre del jugador desde UserService.
   * Si no hay sesión activa, muestra un texto genérico (Manejo de errores —
   * ISO 25010, Fiabilidad: tolerancia a estados nulos).
   */
  readonly playerName: Signal<string> = computed(() => {
    const user: User | null = this.userService.currentUser();
    return user?.username ?? 'Jugador';
  });
}
