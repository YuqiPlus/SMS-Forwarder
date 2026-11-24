package com.smsforwarder.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

abstract class AppScreen extends Activity {
    static final int BG = 0xfff4f7f6;
    static final int INK = 0xff172925;
    static final int MUTED = 0xff50645e;
    static final int ACCENT = 0xff18614e;
    static final int PALE = 0xffe5f1ec;
    static final int STROKE = 0xffdce7e2;
    static final int WHITE = Color.WHITE;
    static final int DANGER = 0xffa83b3e;
    static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;

    enum Symbol { BACK, NEXT, ADD, GEAR, MESSAGE, LINK, SIM, HISTORY }

    LinearLayout page(String title, boolean back, View action) {
        return page(title, back, action, true);
    }

    LinearLayout page(String title, boolean back, View action, boolean scrollable) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets padding = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
                return insets;
            });
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        setContentView(root);

        LinearLayout frame = new LinearLayout(this) {
            @Override
            protected void onMeasure(int width, int height) {
                int measuredWidth = Math.min(MeasureSpec.getSize(width), dp(560));
                super.onMeasure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY), height);
            }
        };
        frame.setOrientation(LinearLayout.VERTICAL);
        root.addView(frame, new FrameLayout.LayoutParams(MATCH, MATCH, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        LinearLayout toolbar = row();
        toolbar.setPadding(dp(back ? 12 : 22), dp(6), dp(18), dp(6));
        toolbar.setMinimumHeight(dp(64));
        add(frame, toolbar, 0);
        if (back) {
            toolbar.addView(iconButton(Symbol.BACK, "返回", false, this::finish), new LinearLayout.LayoutParams(dp(46), dp(46)));
        }
        TextView heading = text(title, back ? 22 : 28, INK, true);
        if (Build.VERSION.SDK_INT >= 28) {
            heading.setAccessibilityHeading(true);
        }
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, WRAP, 1);
        headingParams.setMarginStart(back ? dp(10) : 0);
        toolbar.addView(heading, headingParams);
        if (action != null) {
            toolbar.addView(action, new LinearLayout.LayoutParams(WRAP, dp(46)));
        }

        LinearLayout body = column();
        body.setPadding(dp(20), dp(12), dp(20), dp(24));
        body.setFocusableInTouchMode(true);
        if (scrollable) {
            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setFillViewport(true);
            frame.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1));
            scroll.addView(body);
        } else {
            frame.addView(body, new LinearLayout.LayoutParams(MATCH, 0, 1));
        }
        body.requestFocus();
        return body;
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(MATCH, WRAP);
        layout.topMargin = dp(top);
        parent.addView(child, layout);
    }

    void addSide(LinearLayout parent, View child, int width, int height, int start) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                width < 0 ? width : dp(width), height < 0 ? height : dp(height));
        layout.setMarginStart(dp(start));
        parent.addView(child, layout);
    }

    TextView text(CharSequence value, int size, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        label.setLineSpacing(dp(2), 1);
        return label;
    }

    void section(LinearLayout parent, String label, int margin) {
        TextView title = text(label, 15, INK, true);
        if (Build.VERSION.SDK_INT >= 28) {
            title.setAccessibilityHeading(true);
        }
        add(parent, title, margin);
    }

    LinearLayout surface() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(WHITE, STROKE, 18));
        return card;
    }

    Button action(String label, int fill, int color, Runnable clicked) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(color);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setStateListAnimator(null);
        button.setBackground(ripple(fill, 0, 12));
        button.setOnClickListener(view -> clicked.run());
        return button;
    }

    ImageButton iconButton(Symbol symbol, String label, boolean filled, Runnable clicked) {
        ImageButton button = new ImageButton(this);
        button.setImageDrawable(new LineIcon(symbol, filled ? WHITE : INK));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(label);
        button.setMinimumWidth(dp(48));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(ripple(filled ? ACCENT : PALE, 0, 13));
        button.setOnClickListener(view -> clicked.run());
        return button;
    }

    ImageView icon(Symbol symbol, int color) {
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new LineIcon(symbol, color));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return icon;
    }

    FrameLayout platformBadge(WebhookPlatform platform, int size) {
        int fill = switch (platform) {
            case FEISHU, DINGTALK -> 0xffedf4ff;
            case WECOM -> 0xffeaf5ff;
            case SLACK -> 0xfff8ecf5;
            case DISCORD -> 0xffefedff;
            case AUTO, GENERIC -> PALE;
        };
        int image = switch (platform) {
            case FEISHU -> R.drawable.ic_platform_feishu;
            case WECOM -> R.drawable.ic_platform_wecom;
            case DINGTALK -> R.drawable.ic_platform_dingtalk;
            case SLACK -> R.drawable.ic_platform_slack;
            case DISCORD -> R.drawable.ic_platform_discord;
            case AUTO, GENERIC -> 0;
        };
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(shape(fill, 0, size / 3));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageView mark = image == 0 ? icon(Symbol.LINK, ACCENT) : new ImageView(this);
        if (image != 0) {
            mark.setImageResource(image);
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        int markSize = dp(size * 3 / 5);
        badge.addView(mark, new FrameLayout.LayoutParams(markSize, markSize, Gravity.CENTER));
        return badge;
    }

    void clickableCard(View card, Runnable clicked) {
        card.setBackground(ripple(WHITE, STROKE, 18));
        card.setFocusable(true);
        card.setOnClickListener(view -> clicked.run());
    }

    GradientDrawable shape(int fill, int border, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (border != 0) {
            drawable.setStroke(dp(1), border);
        }
        return drawable;
    }

    RippleDrawable ripple(int fill, int border, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x3218614e), shape(fill, border, radius), shape(WHITE, 0, radius));
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    void showSaveError() {
        Toast.makeText(this, "保存失败，请检查设备存储空间", Toast.LENGTH_LONG).show();
    }

    private final class LineIcon extends Drawable {
        private final Symbol symbol;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        LineIcon(Symbol symbol, int color) {
            this.symbol = symbol;
            paint.setColor(color);
            paint.setStrokeWidth(1.8f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override public int getIntrinsicWidth() { return dp(23); }
        @Override public int getIntrinsicHeight() { return dp(23); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        @Override
        public void draw(Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            switch (symbol) {
                case BACK -> {
                    line(canvas, 14, 6, 8, 12, 14, 18);
                    canvas.drawLine(8, 12, 20, 12, paint);
                }
                case NEXT -> line(canvas, 9, 6, 15, 12, 9, 18);
                case ADD -> {
                    canvas.drawLine(6, 12, 18, 12, paint);
                    canvas.drawLine(12, 6, 12, 18, paint);
                }
                case GEAR -> {
                    Path cog = new Path();
                    for (int segment = 0; segment < 32; segment++) {
                        double angle = (segment + 0.5) * Math.PI / 16;
                        int radius = segment % 4 == 0 || segment % 4 == 3 ? 7 : 10;
                        float x = 12 + (float) Math.cos(angle) * radius;
                        float y = 12 + (float) Math.sin(angle) * radius;
                        if (segment == 0) cog.moveTo(x, y);
                        else cog.lineTo(x, y);
                    }
                    cog.close();
                    canvas.drawPath(cog, paint);
                    canvas.drawCircle(12, 12, 2.9f, paint);
                }
                case MESSAGE -> {
                    line(canvas, 5, 5, 19, 5, 19, 16, 12, 16, 7, 20, 7, 16, 5, 16, 5, 5);
                    canvas.drawLine(8, 9, 16, 9, paint);
                    canvas.drawLine(8, 12.5f, 13, 12.5f, paint);
                }
                case LINK -> {
                    line(canvas, 9, 14, 7, 16, 5, 16, 3, 14, 3, 12, 7, 8, 9, 8, 11, 10);
                    line(canvas, 13, 14, 15, 16, 17, 16, 21, 12, 21, 10, 19, 8, 17, 8, 15, 10);
                    canvas.drawLine(9, 12, 15, 12, paint);
                }
                case SIM -> {
                    line(canvas, 7, 3, 14, 3, 19, 8, 19, 21, 5, 21, 5, 3, 7, 3);
                    canvas.drawRect(8, 11, 16, 17, paint);
                    canvas.drawLine(12, 11, 12, 17, paint);
                }
                case HISTORY -> {
                    canvas.drawArc(4, 4, 21, 21, 205, 310, false, paint);
                    line(canvas, 4, 5, 4, 10, 9, 10);
                    line(canvas, 12, 8, 12, 13, 16, 14.5f);
                }
            }
            canvas.restore();
        }

        private void line(Canvas canvas, float... points) {
            Path path = new Path();
            path.moveTo(points[0], points[1]);
            for (int index = 2; index < points.length; index += 2) {
                path.lineTo(points[index], points[index + 1]);
            }
            canvas.drawPath(path, paint);
        }
    }
}
