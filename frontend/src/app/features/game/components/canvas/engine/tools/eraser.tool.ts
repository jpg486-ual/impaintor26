import { Point, BrushConfig } from '../../../../models/drawing';
import { IDrawingTool, DrawingEngineContext } from './drawing-tool.interface';

export class EraserTool implements IDrawingTool {
  
  onStart(point: Point, context: DrawingEngineContext, config: BrushConfig): void {
    const { ctx } = context;
    
    // Inyectar estado base para la goma
    ctx.globalCompositeOperation = 'source-over';
    this.applyConfig(ctx, config);
    
    // Feedback inmediato del clic
    ctx.beginPath();
    ctx.arc(point.x, point.y, config.thickness / 2, 0, Math.PI * 2);
    ctx.fill();
    
    // Preparar el trazo
    ctx.beginPath();
    ctx.moveTo(point.x, point.y);
    
    context.addPointToStroke(point);
  }

  onMove(point: Point, context: DrawingEngineContext, config: BrushConfig): void {
    const { ctx } = context;
    
    ctx.globalCompositeOperation = 'source-over';
    this.applyConfig(ctx, config);
    
    ctx.lineTo(point.x, point.y);
    ctx.stroke();
    
    context.addPointToStroke(point);
  }

  onEnd(context: DrawingEngineContext): void {
    // Sin acción especial al terminar
  }

  private applyConfig(ctx: CanvasRenderingContext2D, config: BrushConfig): void {
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    // La goma siempre pinta de blanco sin importar el color activo
    ctx.strokeStyle = '#FFFFFF';
    ctx.fillStyle = '#FFFFFF';
    ctx.lineWidth = config.thickness;
  }
}
