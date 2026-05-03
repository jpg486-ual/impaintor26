import { Injectable, signal } from '@angular/core';
import { User } from '../models/user.model';

/**
 * UserService — Responsabilidad Única (SRP): gestiona exclusivamente el estado
 * del usuario autenticado en la sesión activa.
 *
 * ISO 25010 — Mantenibilidad: servicio aislado, inyectable y testeable.
 * ISO 25010 — Fiabilidad: señal reactiva que garantiza consistencia del estado.
 *
 * OCP: El servicio puede extenderse para persistir datos (localStorage / HTTP)
 * sin modificar la interfaz pública que consumen los componentes.
 */
@Injectable({
  providedIn: 'root',
})
export class UserService {
  /**
   * Estado del usuario como señal reactiva (Angular Signals).
   * Se inicializa con un usuario de ejemplo para desarrollo/demo.
   * En producción será reemplazado por datos reales del JWT.
   */
  private readonly _currentUser = signal<User | null>(null);

  /** Señal pública de sólo lectura — cumple el principio de encapsulación. */
  readonly currentUser = this._currentUser.asReadonly();

  /**
   * Actualiza el usuario en sesión (p.e. tras login o refresco de perfil).
   * @param user El usuario autenticado, o null si la sesión expira.
   */
  setCurrentUser(user: User | null): void {
    this._currentUser.set(user);
  }
}
