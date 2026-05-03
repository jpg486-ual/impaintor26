import { Point } from '../../../../models/drawing';

export interface DrawingEngineContext {
  ctx: CanvasRenderingContext2D;
  canvas: HTMLCanvasElement;
  addPointToStroke(point: Point): void;
}

/**
 * Interfaz base para cualquier herramienta de dibujo (Patrón Strategy).
 * OCP: Podemos añadir nuevas herramientas sin modificar el DrawingEngine.
 */
export interface IDrawingTool {
  /**
   * Se ejecuta al iniciar el trazo (ej. mousedown).
   */
  onStart(point: Point, context: DrawingEngineContext, config: any): void;

  /**
   * Se ejecuta durante el arrastre (ej. mousemove).
   */
  onMove(point: Point, context: DrawingEngineContext, config: any): void;

  /**
   * Se ejecuta al soltar (ej. mouseup).
   */
  onEnd(context: DrawingEngineContext): void;
}
