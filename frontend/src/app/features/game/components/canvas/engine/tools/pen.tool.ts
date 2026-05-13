import { Point, BrushConfig } from '../../../../models/drawing';
import { IDrawingTool, DrawingEngineContext } from './drawing-tool.interface';

export class PenTool implements IDrawingTool {
  
  onStart(point: Point, context: DrawingEngineContext, config: BrushConfig): void {
    const { ctx } = context;
    
    // Inyectar estado base para el lápiz
    ctx.globalCompositeOperation = 'source-over';
    this.applyConfig(ctx, config);
    
    // Feedback inmediato del clic (punto gordo)
    ctx.beginPath();
    ctx.arc(point.x, point.y, config.thickness / 2, 0, Math.PI * 2);
    ctx.fill();
    
    // Preparar el trazo
    ctx.beginPath();
    ctx.moveTo(point.x, point.y);
    
    context.addPointToStroke(point);
  }
/* hola */
  onMove(point: Point, context: DrawingEngineContext, config: BrushConfig): void {
    const { ctx } = context;
    
    // Rehidratación explícita defensiva
    ctx.globalCompositeOperation = 'source-over';
    this.applyConfig(ctx, config);
    
    ctx.lineTo(point.x, point.y);
    ctx.stroke();
    
    context.addPointToStroke(point);
  }

  onEnd(context: DrawingEngineContext): void {
    // El trazado continuo no requiere cierre geométrico de Path.
  }

  private applyConfig(ctx: CanvasRenderingContext2D, config: BrushConfig): void {
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = config.color;
    ctx.fillStyle = config.color;
    ctx.lineWidth = config.thickness;
  }
}
