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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

public class PendingIntentCompat {
    private PendingIntentCompat() {
    }

    public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (flags & PendingIntent.FLAG_MUTABLE) != 0)
            return PendingIntent.getActivity(context, requestCode, intent, flags);
        else
            return PendingIntent.getActivity(context, requestCode, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent getService(Context context, int requestCode, @NonNull Intent intent, int flags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (flags & PendingIntent.FLAG_MUTABLE) != 0)
            return PendingIntent.getService(context, requestCode, intent, flags);
        else
            return PendingIntent.getService(context, requestCode, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent getForegroundService(Context context, int requestCode, @NonNull Intent intent, int flags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || (flags & PendingIntent.FLAG_MUTABLE) != 0)
            return PendingIntent.getService(context, requestCode, intent, flags);
        else
            return PendingIntent.getForegroundService(context, requestCode, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }
}
