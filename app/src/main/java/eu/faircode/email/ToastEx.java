package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2022 by Marcel Bokhorst (M66B)
*/

import android.content.Context;
import android.content.res.Resources;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class ToastEx extends Toast {
    public ToastEx(Context context) {
        super(context.getApplicationContext());
    }

    public static ToastEx makeText(Context context, int resId, int duration) throws Resources.NotFoundException {
        return makeText(context, context.getText(resId), duration);
    }

    public static ToastEx makeText(Context context, CharSequence text, int duration) {
        ToastEx toast = new ToastEx(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.toast, null);
        view.setBackground(context.getDrawable(R.drawable.toast_background));

        TextView tv = view.findViewById(android.R.id.message);
        tv.setTextColor(Helper.resolveColor(context, R.attr.colorInfoForeground));
        tv.setText(text);
        toast.setView(view);
        toast.setDuration(duration);

        // <dimen name="design_bottom_navigation_height">56dp</dimen>
        int resId = context.getResources().getIdentifier(
                "design_bottom_navigation_height", "dimen", context.getPackageName());
        int px = Helper.dp2pixels(context, 2 * 56);
        if (resId > 0)
            try {
                px = 2 * context.getResources().getDimensionPixelSize(resId);
            } catch (Throwable ex) {
                Log.e(ex);
            }
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, px);
        return toast;
    }

    public static ToastEx makeTextBw(Context context, CharSequence text, int duration) {
        ToastEx toast = new ToastEx(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.toast, null);
        view.setBackgroundColor(Helper.resolveColor(context, android.R.attr.textColorPrimary));

        TextView tv = view.findViewById(android.R.id.message);
        tv.setTextColor(Helper.resolveColor(context, android.R.attr.textColorPrimaryInverse)); // primary_text_default_material_light
        tv.setText(text);
        toast.setView(view);
        toast.setDuration(duration);
        return toast;
    }

    @Override
    public void show() {
        // https://developer.android.com/preview/features/toasts
        if (Looper.myLooper() != Looper.getMainLooper())
            Log.e("Toast from background");

        // https://stackoverflow.com/questions/56017928/toast-not-showing-in-android-q
        super.show();
    }
}
