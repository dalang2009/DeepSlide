package com.youslide;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.graphics.Color;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(48, 48, 48, 48);
        TextView tv = new TextView(this);
        tv.setText("YouSlide v1.0.4\n\n如果 LSPosed 模块列表里有 YouSlide\n请在 LSPosed 中启用并勾选 YouTube\n然后强制停止 YouTube 再重新打开\n\n检查 /data/local/tmp/youslide.log\n确认注入状态");
        tv.setTextSize(16);
        tv.setTextColor(Color.BLACK);
        layout.addView(tv);
        setContentView(layout);
    }
}