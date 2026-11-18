package io.whale.ncmlite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.View;

/** 胶囊视图：尺寸与观感和 root 版一致（27dp 高 / 11.5sp） */
public class BadgeView extends View {

    private String text = "";
    private String pendingText = null;
    private float progress = -1f;
    private float shown = -1f;
    private float target = -1f;
    private int fillColor = 0xCC1F6FEB;
    private boolean playing = false;
    private int shape = 1;        // 0胶囊 1超椭圆 2不对称 3方角
    private boolean switching = false;
    private float sweep = -1f;
    private int badgeH = 27;        // 高度 dp
    private float textSp = 11.5f;   // 字号 sp
    private float sizeScale = 1f;   // 尺寸倍率
    private int extraPad = 0;       // 额外横向 padding（dp），控制"长度"

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    public BadgeView(Context c) {
        super(c);
    }

    public void setText(String t) {
        String nt = t == null ? "" : t;
        if (!nt.equals(text)) {
            text = nt;
            requestLayout();
        }
        invalidate();
    }

    /** 切换显示模式：上滑淡出 → 下滑淡入（可打断） */
    public void setTextSwitch(final String t) {
        final String nt = t == null ? "" : t;
        if (nt.equals(text) && pendingText == null) return;

        animate().cancel();
        switching = false;

        boolean animOn = Store.getInt("switchanim", 1) == 1;
        if (!animOn) {
            pendingText = null;
            text = nt;
            setAlpha(1f);
            setTranslationY(0f);
            setScaleX(1f);
            setScaleY(1f);
            requestLayout();
            invalidate();
            setScaleX(0.93f);
            setScaleY(0.93f);
            animate().scaleX(1f).scaleY(1f).setDuration(170).start();
            return;
        }

        pendingText = nt;
        switching = true;
        final float dy = dp(7);
        animate().alpha(0f).translationY(-dy).scaleX(0.95f).scaleY(0.95f)
                .setDuration(95)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        text = pendingText != null ? pendingText : nt;
                        pendingText = null;
                        requestLayout();
                        setTranslationY(dy);
                        animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                                .setDuration(130)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        switching = false;
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    public void setProgress(float p) {
        progress = p;
        target = p;
        if (p < 0f) shown = -1f;
        postInvalidateOnAnimation();
    }

    public void setFill(int c) {
        fillColor = c;
        invalidate();
    }

    public void setFillAnimated(final int c) {
        final int from = fillColor;
        if (from == c) return;
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(260);
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                fillColor = blend(from, c, f);
                invalidate();
            }
        });
        va.start();
    }

    public int getFill() {
        return fillColor;
    }

    public void setSizeScale(float f) {
        sizeScale = Math.max(0.6f, Math.min(2.0f, f));
        requestLayout();
        invalidate();
    }

    public float getSizeScale() {
        return sizeScale;
    }

    public void setExtraPad(int dp) {
        extraPad = Math.max(0, Math.min(60, dp));
        requestLayout();
        invalidate();
    }

    public void setSize(int hDp, float sp) {
        badgeH = Math.max(16, hDp);
        textSp = Math.max(8f, sp);
        requestLayout();
        invalidate();
    }

    public void setShape(int s) {
        shape = s;
        requestLayout();
        invalidate();
    }

    public void setPlaying(boolean b) {
        if (playing != b) {
            playing = b;
            invalidate();
        }
    }

    public void sweepOnce() {
        sweep = 1f;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        paint.setTextSize(sp(11.5f * sizeScale));
        String t = pendingText != null ? pendingText : text;
        float tw = paint.measureText(t);
        setMeasuredDimension((int) (tw + (dp(24) + dp(extraPad)) * sizeScale),
                (int) (dp(27) * sizeScale));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float full = Math.min(h / 2f, w / 2f);
        rect.set(0, 0, w, h);
        buildShape(w, h, full);

        // 底：竖向渐变
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h,
                scale(fillColor, 1.12f, 165), scale(fillColor, 0.84f, 165),
                Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);

        // 进度填充
        if (target >= 0f) {
            if (shown < 0f) shown = target;
            shown += (target - shown) * 0.18f;
            if (Math.abs(target - shown) < 0.002f) shown = target;
            else postInvalidateOnAnimation();
            if (shown > 0.005f) {
                canvas.save();
                canvas.clipRect(0, 0, w * shown, h);
                paint.setShader(new LinearGradient(0, 0, 0, h,
                        scale(fillColor, 1.3f, 200), scale(fillColor, 1.0f, 200),
                        Shader.TileMode.CLAMP));
                canvas.drawPath(path, paint);
                paint.setShader(null);
                canvas.restore();
            }
        }

        // 进度前沿光带
        if (target >= 0f && shown > 0.02f && shown < 0.995f) {
            float edge = w * shown;
            paint.setShader(new LinearGradient(Math.max(0f, edge - dp(13)), 0, edge + 1f, 0,
                    new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null,
                    Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);
        }

        // 切歌光扫
        if (sweep >= 0f) {
            float cx = (1f - sweep) * (w + w * 0.5f) - w * 0.25f;
            float bw = Math.max(dp(18), w * 0.30f);
            paint.setShader(new LinearGradient(cx, 0, cx + bw, 0,
                    new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null,
                    Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);
            sweep -= 0.028f;
            if (sweep >= 0f) postInvalidateOnAnimation();
        }

        // 顶部高光
        paint.setColor(0x12FFFFFF);
        canvas.save();
        canvas.clipRect(0, 0, w, h * 0.46f);
        canvas.drawPath(path, paint);
        canvas.restore();

        // 描边
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x26FFFFFF);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        // 左侧状态条
        float barW = dp(3) * sizeScale, barX = dp(5) * sizeScale;
        paint.setColor(playing ? 0xBBFFFFFF : 0x40FFFFFF);
        canvas.drawRoundRect(barX, h * 0.30f, barX + barW, h * 0.70f,
                barW / 2f, barW / 2f, paint);

        // 文字
        String draw = pendingText != null ? pendingText : text;
        paint.setTextSize(sp(11.5f * sizeScale));
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseY = h / 2f - (fm.ascent + fm.descent) / 2f;
        paint.setColor(0x55000000);
        canvas.drawText(draw, w / 2f, baseY + dp(1), paint);
        paint.setColor(0xFFFFFFFF);
        canvas.drawText(draw, w / 2f, baseY, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void buildShape(float w, float h, float full) {
        path.reset();
        if (shape == 0) {
            path.addRoundRect(new RectF(0, 0, w, h), full, full, Path.Direction.CW);
            return;
        }
        if (shape == 3) {
            float rr = full * 0.34f;
            path.addRoundRect(new RectF(0, 0, w, h), rr, rr, Path.Direction.CW);
            return;
        }
        if (shape == 2) {
            float rl = full, rr2 = full * 0.30f;
            float cl = rl * 0.62f, cr = rr2 * 0.62f;
            path.moveTo(rl, 0);
            path.lineTo(w - rr2, 0);
            path.cubicTo(w - rr2 + cr, 0, w, rr2 - cr, w, rr2);
            path.lineTo(w, h - rr2);
            path.cubicTo(w, h - rr2 + cr, w - rr2 + cr, h, w - rr2, h);
            path.lineTo(rl, h);
            path.cubicTo(rl - cl, h, 0, h - rl + cl, 0, h - rl);
            path.lineTo(0, rl);
            path.cubicTo(0, rl - cl, rl - cl, 0, rl, 0);
            path.close();
            return;
        }
        float r = full, c = r * 0.62f;
        path.moveTo(r, 0);
        path.lineTo(w - r, 0);
        path.cubicTo(w - r + c, 0, w, r - c, w, r);
        path.lineTo(w, h - r);
        path.cubicTo(w, h - r + c, w - r + c, h, w - r, h);
        path.lineTo(r, h);
        path.cubicTo(r - c, h, 0, h - r + c, 0, h - r);
        path.lineTo(0, r);
        path.cubicTo(0, r - c, r - c, 0, r, 0);
        path.close();
    }

    private int blend(int a, int b, float f) {
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * f);
        int rr = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int gg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    private int scale(int color, float f, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((color & 0xFF) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
