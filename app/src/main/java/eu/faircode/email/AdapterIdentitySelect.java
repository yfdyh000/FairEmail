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
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class AdapterIdentitySelect extends ArrayAdapter<TupleIdentityEx> {
    private Context context;
    private List<TupleIdentityEx> identities;
    private boolean hasColor = false;

    AdapterIdentitySelect(@NonNull Context context, List<TupleIdentityEx> identities) {
        super(context, 0, identities);
        this.context = context;
        this.identities = identities;

        for (TupleIdentityEx identity : identities)
            if (identity.color != null) {
                hasColor = true;
                break;
            }
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return getLayout(position, convertView, parent, R.layout.spinner_identity);
    }

    @Override
    public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
        return getLayout(position, convertView, parent, R.layout.spinner_identity);
    }

    private View getLayout(int position, View convertView, ViewGroup parent, int resid) {
        View view = LayoutInflater.from(context).inflate(resid, parent, false);

        TupleIdentityEx identity = identities.get(position);

        View vwColor = view.findViewById(R.id.vwColor);
        TextView text1 = view.findViewById(android.R.id.text1);
        TextView text2 = view.findViewById(android.R.id.text2);
        TextView tvExtra = view.findViewById(R.id.tvExtra);

        vwColor.setBackgroundColor(identity.color == null ? Color.TRANSPARENT : identity.color);
        vwColor.setVisibility(hasColor ? View.VISIBLE : View.GONE);

        boolean single = (identities.size() == 1 && identity.cc == null && identity.bcc == null);

        if (single)
            text1.setText(identity.getDisplayName() + " <" + identity.email + ">");
        else {
            text1.setText(identity.getDisplayName() + (identity.primary ? " ★" : ""));
            text2.setText(identity.accountName + "/" + identity.email);
        }

        tvExtra.setText((identity.cc == null ? "" : "+CC") + (identity.bcc == null ? "" : "+BCC"));

        text2.setVisibility(single ? View.GONE : View.VISIBLE);
        tvExtra.setVisibility(identity.cc == null && identity.bcc == null ? View.GONE : View.VISIBLE);

        return view;
    }
}
