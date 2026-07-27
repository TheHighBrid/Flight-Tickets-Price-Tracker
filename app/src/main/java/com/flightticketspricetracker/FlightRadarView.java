package com.flightticketspricetracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public final class FlightRadarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path routePath = new Path();
    private String originLabel = "YOW";
    private String destinationLabel = "CMN";

    public FlightRadarView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public FlightRadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setRouteLabels(String origin, String destination) {
        originLabel = code(origin, "YOW");
        destinationLabel = code(destination, "CMN");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float radius = dp(22);
        RectF bounds = new RectF(0, 0, width, height);
        canvas.save();
        canvas.clipRoundRect(bounds, radius, radius);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                0, 0, width, height,
                FlightTheme.NAVY_2,
                FlightTheme.DEEP_BLUE,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(bounds, paint);
        paint.setShader(null);

        drawGlow(canvas, width * 0.84f, height * 0.12f, Math.min(width, height) * 0.36f);
        drawRadarGrid(canvas, width, height);
        drawRoute(canvas, width, height);
        drawLiveBadge(canvas, width);

        canvas.restore();
    }

    private void drawGlow(Canvas canvas, float x, float y, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(FlightTheme.withAlpha(FlightTheme.CYAN, 0.16f));
        paint.setShadowLayer(radius * 0.6f, 0, 0, FlightTheme.withAlpha(FlightTheme.CYAN, 0.55f));
        canvas.drawCircle(x, y, radius, paint);
        paint.clearShadowLayer();
    }

    private void drawRadarGrid(Canvas canvas, float width, float height) {
        float centerX = width * 0.50f;
        float centerY = height * 0.67f;
        float max = Math.max(width, height) * 0.64f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(FlightTheme.withAlpha(FlightTheme.AQUA, 0.20f));
        for (int i = 1; i <= 4; i++) {
            float radius = max * i / 4f;
            canvas.drawCircle(centerX, centerY, radius, paint);
        }

        paint.setColor(FlightTheme.withAlpha(FlightTheme.AQUA, 0.12f));
        for (int i = -3; i <= 3; i++) {
            float x = centerX + i * width / 7f;
            canvas.drawLine(x, 0, x, height, paint);
        }
        for (int i = 1; i <= 4; i++) {
            float y = i * height / 5f;
            canvas.drawLine(0, y, width, y, paint);
        }

        paint.setStrokeWidth(dp(2));
        paint.setColor(FlightTheme.withAlpha(FlightTheme.CYAN, 0.26f));
        canvas.drawLine(centerX, 0, centerX, height, paint);
    }

    private void drawRoute(Canvas canvas, float width, float height) {
        float startX = width * 0.14f;
        float startY = height * 0.72f;
        float endX = width * 0.86f;
        float endY = height * 0.34f;

        routePath.reset();
        routePath.moveTo(startX, startY);
        routePath.cubicTo(
                width * 0.34f, height * 0.30f,
                width * 0.62f, height * 0.84f,
                endX, endY
        );

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(7));
        paint.setColor(FlightTheme.withAlpha(FlightTheme.CYAN, 0.12f));
        canvas.drawPath(routePath, paint);

        paint.setStrokeWidth(dp(2.4f));
        paint.setColor(FlightTheme.AQUA);
        paint.setShadowLayer(dp(8), 0, 0, FlightTheme.CYAN);
        canvas.drawPath(routePath, paint);
        paint.clearShadowLayer();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(FlightTheme.CYAN);
        canvas.drawCircle(startX, startY, dp(5), paint);
        canvas.drawCircle(endX, endY, dp(5), paint);
        paint.setColor(FlightTheme.WHITE);
        canvas.drawCircle(startX, startY, dp(2), paint);
        canvas.drawCircle(endX, endY, dp(2), paint);

        paint.setTextSize(dp(12));
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setColor(FlightTheme.TEXT);
        canvas.drawText(originLabel, startX - dp(8), startY + dp(22), paint);
        canvas.drawText(destinationLabel, endX - dp(10), endY - dp(12), paint);

        PathMeasure measure = new PathMeasure(routePath, false);
        float[] pos = new float[2];
        float[] tan = new float[2];
        measure.getPosTan(measure.getLength() * 0.57f, pos, tan);
        float angle = (float) Math.toDegrees(Math.atan2(tan[1], tan[0]));

        canvas.save();
        canvas.translate(pos[0], pos[1]);
        canvas.rotate(angle + 90f);
        Path plane = new Path();
        plane.moveTo(0, -dp(12));
        plane.lineTo(dp(4), -dp(2));
        plane.lineTo(dp(11), dp(2));
        plane.lineTo(dp(11), dp(5));
        plane.lineTo(dp(3), dp(3));
        plane.lineTo(dp(2), dp(11));
        plane.lineTo(-dp(2), dp(11));
        plane.lineTo(-dp(3), dp(3));
        plane.lineTo(-dp(11), dp(5));
        plane.lineTo(-dp(11), dp(2));
        plane.lineTo(-dp(4), -dp(2));
        plane.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(FlightTheme.WHITE);
        paint.setShadowLayer(dp(7), 0, dp(2), FlightTheme.withAlpha(FlightTheme.NAVY, 0.7f));
        canvas.drawPath(plane, paint);
        paint.clearShadowLayer();
        canvas.restore();
    }

    private void drawLiveBadge(Canvas canvas, float width) {
        float left = width - dp(76);
        float top = dp(14);
        RectF badge = new RectF(left, top, width - dp(14), top + dp(28));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(FlightTheme.withAlpha(FlightTheme.NAVY, 0.72f));
        canvas.drawRoundRect(badge, dp(14), dp(14), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(FlightTheme.withAlpha(FlightTheme.CYAN, 0.7f));
        canvas.drawRoundRect(badge, dp(14), dp(14), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(FlightTheme.SUCCESS);
        canvas.drawCircle(left + dp(13), top + dp(14), dp(4), paint);
        paint.setTextSize(dp(10));
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setColor(FlightTheme.TEXT);
        canvas.drawText("LIVE", left + dp(23), top + dp(18), paint);
    }

    private String code(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim().toUpperCase();
        int open = trimmed.lastIndexOf('(');
        int close = trimmed.lastIndexOf(')');
        if (open >= 0 && close > open + 1) {
            String candidate = trimmed.substring(open + 1, close);
            if (candidate.length() == 3) return candidate;
        }
        if (trimmed.length() == 3) return trimmed;
        return fallback;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
