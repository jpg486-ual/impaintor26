import { Component, inject } from '@angular/core';
import { AuthService } from '../../../../core/services/auth.service';

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
        <span class="player-name">{{ playerName }}</span>
      </h1>
      <p class="welcome-subtitle">¿Listo para pintar… o engañar?</p>
      <div class="banner-divider" aria-hidden="true"></div>
    </header>
  `,
  styleUrl: './welcome-banner.component.css',
})
export class WelcomeBannerComponent {
  private readonly authService = inject(AuthService);

  /**
   * Obtiene el nombre del jugador desde el AuthService.
   * Si no hay sesión activa, muestra un texto genérico.
   */
  readonly playerName: string = this.authService.getCurrentUser()?.username ?? 'Jugador';
}
