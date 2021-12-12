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

    Copyright 2018-2021 by Marcel Bokhorst (M66B)
*/

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.QuoteSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import org.jsoup.nodes.Document;

import java.util.concurrent.ExecutorService;

public class EditTextCompose extends FixedEditText {
    private boolean raw = false;
    private ISelection selectionListener = null;
    private IInputContentListener inputContentListener = null;

    private static final ExecutorService executor =
            Helper.getBackgroundExecutor(1, "paste");

    public EditTextCompose(Context context) {
        super(context);
        Helper.setKeyboardIncognitoMode(this, context);
    }

    public EditTextCompose(Context context, AttributeSet attrs) {
        super(context, attrs);
        Helper.setKeyboardIncognitoMode(this, context);
    }

    public EditTextCompose(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Helper.setKeyboardIncognitoMode(this, context);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState, this.raw);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        setRaw(savedState.getRaw());
    }

    @Override
    protected void onAttachedToWindow() {
        // Spellchecker workaround
        boolean enabled = isEnabled();
        super.setEnabled(true);
        super.onAttachedToWindow();
        super.setEnabled(enabled);
    }

    public void setRaw(boolean raw) {
        this.raw = raw;
    }

    public boolean isRaw() {
        return raw;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (selectionListener != null)
            selectionListener.onSelected(hasSelection());
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        try {
            if (id == android.R.id.copy) {
                int start = getSelectionStart();
                int end = getSelectionEnd();
                if (start > end) {
                    int s = start;
                    start = end;
                    end = s;
                }

                Context context = getContext();
                ClipboardManager cbm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (start != end && cbm != null) {
                    CharSequence selected = getEditableText().subSequence(start, end);
                    if (selected instanceof Spanned) {
                        String html = HtmlHelper.toHtml((Spanned) selected, context);
                        cbm.setPrimaryClip(ClipData.newHtmlText(context.getString(R.string.app_name), selected, html));
                        setSelection(end);
                        return false;
                    }
                }
            } else if (id == android.R.id.paste) {
                final Context context = getContext();

                ClipboardManager cbm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cbm == null || !cbm.hasPrimaryClip())
                    return false;

                ClipData.Item item = cbm.getPrimaryClip().getItemAt(0);

                final String html;
                String h = (raw ? null : item.getHtmlText());
                if (h == null) {
                    CharSequence text = item.getText();
                    if (text == null)
                        return false;
                    if (raw)
                        html = text.toString();
                    else
                        html = "<div>" + HtmlHelper.formatPre(text.toString(), false) + "</div>";
                } else
                    html = h;

                final int colorPrimary = Helper.resolveColor(context, R.attr.colorPrimary);
                final int colorBlockquote = Helper.resolveColor(context, R.attr.colorBlockquote, colorPrimary);
                final int quoteGap = context.getResources().getDimensionPixelSize(R.dimen.quote_gap_size);
                final int quoteStripe = context.getResources().getDimensionPixelSize(R.dimen.quote_stripe_width);

                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SpannableStringBuilder ssb;
                            if (raw)
                                ssb = new SpannableStringBuilderEx(html);
                            else {
                                Document document = HtmlHelper.sanitizeCompose(context, html, false);
                                Spanned paste = HtmlHelper.fromDocument(context, document, new Html.ImageGetter() {
                                    @Override
                                    public Drawable getDrawable(String source) {
                                        return ImageHelper.decodeImage(context,
                                                -1, source, true, 0, 1.0f, EditTextCompose.this);
                                    }
                                }, null);

                                ssb = new SpannableStringBuilderEx(paste);
                                QuoteSpan[] spans = ssb.getSpans(0, ssb.length(), QuoteSpan.class);
                                for (QuoteSpan span : spans) {
                                    QuoteSpan q;
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                                        q = new QuoteSpan(colorBlockquote);
                                    else
                                        q = new QuoteSpan(colorBlockquote, quoteStripe, quoteGap);
                                    ssb.setSpan(q,
                                            ssb.getSpanStart(span),
                                            ssb.getSpanEnd(span),
                                            ssb.getSpanFlags(span));
                                    ssb.removeSpan(span);
                                }
                            }

                            ApplicationEx.getMainHandler().post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        int start = getSelectionStart();
                                        int end = getSelectionEnd();

                                        if (start < 0)
                                            start = 0;
                                        if (end < 0)
                                            end = 0;

                                        if (start > end) {
                                            int tmp = start;
                                            start = end;
                                            end = tmp;
                                        }

                                        if (start == end)
                                            getText().insert(start, ssb);
                                        else
                                            getText().replace(start, end, ssb);
                                    } catch (Throwable ex) {
                                        Log.e(ex);
                                        /*
                                            java.lang.RuntimeException: PARAGRAPH span must start at paragraph boundary
                                                    at android.text.SpannableStringBuilder.setSpan(SpannableStringBuilder.java:619)
                                                    at android.text.SpannableStringBuilder.change(SpannableStringBuilder.java:391)
                                                    at android.text.SpannableStringBuilder.replace(SpannableStringBuilder.java:496)
                                                    at android.text.SpannableStringBuilder.replace(SpannableStringBuilder.java:454)
                                                    at android.text.SpannableStringBuilder.replace(SpannableStringBuilder.java:33)
                                                    at android.widget.TextView.paste(TextView.java:8891)
                                                    at android.widget.TextView.onTextContextMenuItem(TextView.java:8706)
                                         */
                                    }
                                }
                            });
                        } catch (Throwable ex) {
                            Log.e(ex);
                        }
                    }
                });

                return true;
            }

            return super.onTextContextMenuItem(id);
        } catch (Throwable ex) {
            Log.e(ex);
            return false;
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
        //https://developer.android.com/guide/topics/text/image-keyboard
        InputConnection ic = super.onCreateInputConnection(editorInfo);
        if (ic == null)
            return null;

        EditorInfoCompat.setContentMimeTypes(editorInfo, new String[]{"image/*"});

        return InputConnectionCompat.createWrapper(ic, editorInfo, new InputConnectionCompat.OnCommitContentListener() {
            @Override
            public boolean onCommitContent(InputContentInfoCompat info, int flags, Bundle opts) {
                Log.i("Uri=" + info.getContentUri());
                try {
                    if (inputContentListener == null)
                        throw new IllegalArgumentException("InputContent listener not set");

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 &&
                            (flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0)
                        info.requestPermission();

                    inputContentListener.onInputContent(info.getContentUri());
                    return true;
                } catch (Throwable ex) {
                    Log.w(ex);
                    return false;
                }
            }
        });
    }

    void setInputContentListener(IInputContentListener listener) {
        this.inputContentListener = listener;
    }

    interface IInputContentListener {
        void onInputContent(Uri uri);
    }

    void setSelectionListener(ISelection listener) {
        this.selectionListener = listener;
    }

    interface ISelection {
        void onSelected(boolean selection);
    }

    static class SavedState extends View.BaseSavedState {
        private boolean raw;

        private SavedState(Parcelable superState, boolean raw) {
            super(superState);
            this.raw = raw;
        }

        private SavedState(Parcel in) {
            super(in);
            raw = (in.readInt() != 0);
        }

        public boolean getRaw() {
            return this.raw;
        }

        @Override
        public void writeToParcel(Parcel destination, int flags) {
            super.writeToParcel(destination, flags);
            destination.writeInt(raw ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
