import { Component } from '@angular/core';

/**
 * HomeFooterComponent — Responsabilidad Única (SRP):
 * muestra la información de pie de página de la pantalla principal.
 *
 * ISO 25010 — Usabilidad (estética e interfaz): pie coherente con
 * la temática del juego, ligero y no intrusivo.
 */
@Component({
  selector: 'app-home-footer',
  standalone: true,
  template: `
    <footer class="home-footer" role="contentinfo">
      <div class="footer-ornament" aria-hidden="true">⚜</div>
      <p class="footer-text">
        Impaintor &copy; 2026 — Dibuja. Engaña. Descubre.
      </p>
      <div class="footer-ornament" aria-hidden="true">⚜</div>
    </footer>
  `,
  styleUrl: './home-footer.component.css',
})
export class HomeFooterComponent {}
