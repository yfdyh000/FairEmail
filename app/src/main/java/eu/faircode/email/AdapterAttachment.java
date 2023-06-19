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

    Copyright 2018-2023 by Marcel Bokhorst (M66B)
*/

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ShareCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.mail.Part;

public class AdapterAttachment extends RecyclerView.Adapter<AdapterAttachment.ViewHolder> {
    private Fragment parentFragment;

    private Context context;
    private LifecycleOwner owner;
    private LayoutInflater inflater;

    private boolean readonly;
    private AdapterMessage.IProperties properties;

    private boolean vt_enabled;
    private String vt_apikey;
    private boolean debug;
    private int dp12;
    private int dp36;
    private int textColorTertiary;
    private int colorWarning;

    private List<EntityAttachment> items = new ArrayList<>();

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private View view;
        private ImageButton ibDelete;
        private ImageView ivType;
        private ImageView ivDisposition;
        private TextView tvName;
        private TextView tvSize;
        private ImageView ivStatus;
        private ImageButton ibSave;
        private ImageButton ibScan;
        private TextView tvType;
        private TextView tvError;
        private ProgressBar progressbar;

        private TwoStateOwner powner = new TwoStateOwner(owner, "AttachmentPopup");

        ViewHolder(View itemView) {
            super(itemView);

            view = itemView.findViewById(R.id.clItem);
            ibDelete = itemView.findViewById(R.id.ibDelete);
            ivType = itemView.findViewById(R.id.ivType);
            tvName = itemView.findViewById(R.id.tvName);
            tvSize = itemView.findViewById(R.id.tvSize);
            ivStatus = itemView.findViewById(R.id.ivStatus);
            ibSave = itemView.findViewById(R.id.ibSave);
            ibScan = itemView.findViewById(R.id.ibScan);
            tvType = itemView.findViewById(R.id.tvType);
            ivDisposition = itemView.findViewById(R.id.ivDisposition);
            tvError = itemView.findViewById(R.id.tvError);
            progressbar = itemView.findViewById(R.id.progressbar);
        }

        private void wire() {
            view.setOnClickListener(this);
            ibDelete.setOnClickListener(this);
            ibSave.setOnClickListener(this);
            ibScan.setOnClickListener(this);
            view.setOnLongClickListener(this);
        }

        private void unwire() {
            view.setOnClickListener(null);
            ibDelete.setOnClickListener(null);
            ibSave.setOnClickListener(null);
            ibScan.setOnClickListener(null);
            view.setOnLongClickListener(null);
        }

        private void bindTo(EntityAttachment attachment) {
            view.setAlpha(!attachment.isAttachment() ? Helper.LOW_LIGHT : 1.0f);

            ViewGroup.MarginLayoutParams lparam = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            lparam.setMarginStart(attachment.subsequence == null ? 0 : dp12);
            view.setLayoutParams(lparam);

            ibDelete.setVisibility(readonly && !BuildConfig.DEBUG ? View.GONE : View.VISIBLE);

            if (!readonly && attachment.isImage()) {
                if (attachment.available) {
                    Bitmap bm = ImageHelper.decodeImage(
                            attachment.getFile(context), attachment.getMimeType(), dp36);
                    if (bm == null)
                        ivType.setImageResource(R.drawable.twotone_broken_image_24);
                    else
                        ivType.setImageBitmap(bm);
                } else
                    ivType.setImageResource(R.drawable.twotone_hourglass_top_24);
                ivDisposition.setVisibility(View.GONE);
            } else {
                int resid = 0;
                String extension = Helper.guessExtension(attachment.getMimeType());
                if (extension != null)
                    resid = context.getResources().getIdentifier("file_" + extension, "drawable", context.getPackageName());
                if (resid == 0)
                    ivType.setImageDrawable(null);
                else
                    ivType.setImageResource(resid);

                ivDisposition.setImageLevel(Part.INLINE.equals(attachment.disposition) ? 1 : 0);
                ivDisposition.setVisibility(
                        Part.ATTACHMENT.equals(attachment.disposition) ||
                                Part.INLINE.equals(attachment.disposition)
                                ? View.VISIBLE : View.INVISIBLE);
            }

            boolean dangerous = Helper.DANGEROUS_EXTENSIONS.contains(Helper.getExtension(attachment.name));
            tvName.setText(attachment.name);
            tvName.setTextColor(dangerous ? colorWarning : textColorTertiary);
            tvName.setTypeface(null, dangerous ? Typeface.BOLD : Typeface.NORMAL);

            if (attachment.size != null)
                tvSize.setText(Helper.humanReadableByteCount(attachment.size));
            tvSize.setVisibility(attachment.size == null ? View.GONE : View.VISIBLE);

            if (attachment.available) {
                ivStatus.setImageResource(R.drawable.twotone_visibility_24);
                ivStatus.setVisibility(View.VISIBLE);
            } else {
                if (attachment.progress == null) {
                    ivStatus.setImageResource(R.drawable.twotone_cloud_download_24);
                    ivStatus.setVisibility(View.VISIBLE);
                } else
                    ivStatus.setVisibility(View.GONE);
            }

            ibSave.setVisibility(attachment.available ? View.VISIBLE : View.GONE);
            ibScan.setVisibility(attachment.available &&
                    vt_enabled && !TextUtils.isEmpty(vt_apikey) && !BuildConfig.PLAY_STORE_RELEASE
                    ? View.VISIBLE : View.GONE);

            if (attachment.progress != null)
                progressbar.setProgress(attachment.progress);
            progressbar.setVisibility(
                    attachment.progress == null || attachment.available ? View.GONE : View.VISIBLE);

            StringBuilder sb = new StringBuilder();
            sb.append(attachment.type);
            if (debug || BuildConfig.DEBUG) {
                if (attachment.cid != null) {
                    sb.append(' ').append(attachment.cid);
                    if (attachment.related != null)
                        sb.append(' ').append(attachment.related);
                }
                if (attachment.isEncryption())
                    sb.append(' ').append(attachment.encryption);
            }
            tvType.setText(sb.toString());

            tvError.setText(attachment.error);
            tvError.setTextColor(Helper.resolveColor(context, attachment.available ? R.attr.colorWarning : R.attr.colorError));
            tvError.setVisibility(attachment.error == null ? View.GONE : View.VISIBLE);

            if (properties != null) {
                String aid = properties.getValue("attachment");
                if (aid != null) {
                    if (attachment.id.equals(Long.parseLong(aid)) &&
                            attachment.available &&
                            attachment.size != null && attachment.size > 0) {
                        properties.setValue("attachment", null);
                        onView(attachment);
                    }
                }
            }
        }

        @Override
        public void onClick(View view) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return;

            EntityAttachment attachment = items.get(pos);
            if (attachment == null)
                return;

            int id = view.getId();
            if (id == R.id.ibDelete)
                onDelete(attachment);
            else if (id == R.id.ibSave)
                onSave(attachment);
            else if (id == R.id.ibScan)
                onScan(attachment);
            else {
                if (attachment.available)
                    onView(attachment);
                else {
                    if (attachment.progress == null)
                        if (attachment.subsequence == null)
                            onDownload(attachment);
                        else if (!TextUtils.isEmpty(attachment.error))
                            ToastEx.makeText(context, attachment.error, Toast.LENGTH_LONG).show();
                }
            }
        }

        @Override
        public boolean onLongClick(View view) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return false;

            EntityAttachment attachment = items.get(pos);
            if (attachment == null || !attachment.available)
                return false;

            if (readonly)
                return onShare(attachment);
            else {
                PopupMenuLifecycle popupMenu = new PopupMenuLifecycle(context, powner, view);

                popupMenu.getMenu().add(Menu.NONE, R.string.title_share, 1, R.string.title_share);
                popupMenu.getMenu().add(Menu.NONE, R.string.title_zip, 2, R.string.title_zip)
                        .setEnabled(!attachment.isInline() && !attachment.isCompressed());

                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        int itemId = item.getItemId();
                        if (itemId == R.string.title_share)
                            return onShare(attachment);
                        else if (itemId == R.string.title_zip)
                            return onZip(attachment);
                        return false;
                    }
                });

                popupMenu.show();

                return true;
            }
        }

        private boolean onShare(final EntityAttachment attachment) {
            try {
                new ShareCompat.IntentBuilder(context)
                        .setType(attachment.getMimeType())
                        .addStream(attachment.getUri(context))
                        .setChooserTitle(R.string.title_select_app)
                        .startChooser();

                return true;
            } catch (Throwable ex) {
                /*
                    java.lang.IllegalArgumentException: Failed to resolve canonical path for ...
                      at androidx.core.content.FileProvider$SimplePathStrategy.getUriForFile(SourceFile:730)
                      at androidx.core.content.FileProvider.getUriForFile(SourceFile:418)
                 */
                Log.unexpectedError(parentFragment.getParentFragmentManager(), ex);
                return false;
            }
        }

        private boolean onZip(final EntityAttachment attachment) {
            Bundle args = new Bundle();
            args.putLong("id", attachment.id);

            new SimpleTask<Void>() {
                @Override
                protected Void onExecute(Context context, Bundle args) throws Throwable {
                    long id = args.getLong("id");

                    DB db = DB.getInstance(context);
                    EntityAttachment attachment = db.attachment().getAttachment(id);
                    if (attachment != null)
                        attachment.zip(context);

                    return null;
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Log.unexpectedError(parentFragment.getParentFragmentManager(), ex);
                }
            }.execute(context, owner, args, "attachment:zip");

            return true;
        }

        private void onDelete(final EntityAttachment attachment) {
            Bundle args = new Bundle();
            args.putLong("id", attachment.id);
            args.putBoolean("readonly", readonly);

            new SimpleTask<Void>() {
                @Override
                protected Void onExecute(Context context, Bundle args) {
                    long id = args.getLong("id");
                    boolean readonly = args.getBoolean("readonly");

                    EntityAttachment attachment;

                    DB db = DB.getInstance(context);
                    try {
                        db.beginTransaction();

                        attachment = db.attachment().getAttachment(id);
                        if (attachment == null)
                            return null;

                        if (readonly) {
                            EntityMessage message = db.message().getMessage(attachment.message);
                            if (message == null)
                                return null;
                            EntityOperation.queue(context, message, EntityOperation.ATTACHMENT, attachment.id, true);
                            db.message().setMessageUiHide(message.id, true);
                        } else
                            db.attachment().deleteAttachment(attachment.id);

                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }

                    if (!readonly)
                        attachment.getFile(context).delete();

                    return null;
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Log.unexpectedError(parentFragment.getParentFragmentManager(), ex);
                }
            }.execute(context, owner, args, "attachment:delete");
        }

        private void onSave(EntityAttachment attachment) {
            ((FragmentBase) parentFragment).onStoreAttachment(attachment);
        }

        private void onScan(EntityAttachment attachment) {
            Bundle args = new Bundle();
            args.putString("apiKey", vt_apikey);
            args.putString("name", attachment.name);
            args.putSerializable("file", attachment.getFile(context));

            FragmentDialogVirusTotal fragment = new FragmentDialogVirusTotal();
            fragment.setArguments(args);
            fragment.show(parentFragment.getParentFragmentManager(), "attachment:scan");
        }

        private void onView(EntityAttachment attachment) {
            String ext = Helper.getExtension(attachment.name);
            String extension = (ext == null ? null : ext.toLowerCase(Locale.ROOT));

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean confirm = prefs.getBoolean("confirm_files", true);
            boolean confirm_type = prefs.getBoolean(extension + ".confirm_files", true);
            if (extension != null && confirm && confirm_type) {
                View view = LayoutInflater.from(context).inflate(R.layout.dialog_view_file, null);
                TextView tvFile = view.findViewById(R.id.tvFile);
                TextView tvType = view.findViewById(R.id.tvType);
                CheckBox cbNotAgainType = view.findViewById(R.id.cbNotAgainType);
                CheckBox cbNotAgain = view.findViewById(R.id.cbNotAgain);

                tvFile.setText(context.getString(R.string.title_ask_view_file, attachment.name));
                tvType.setText(attachment.getMimeType());
                String msg = context.getString(R.string.title_no_ask_for_again, "<b>" + "." + extension + "</b>");
                cbNotAgainType.setText(HtmlHelper.fromHtml(msg, context));

                cbNotAgainType.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                        prefs.edit().putBoolean(extension + ".confirm_files", false).apply();
                    }
                });

                cbNotAgain.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                        prefs.edit().putBoolean("confirm_files", false).apply();
                        cbNotAgainType.setEnabled(!isChecked);
                    }
                });

                new AlertDialog.Builder(context)
                        .setView(view)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                onViewConfirmed(attachment);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else
                onViewConfirmed(attachment);
        }

        private void onViewConfirmed(EntityAttachment attachment) {
            try {
                String title = (attachment.name == null ? attachment.cid : attachment.name);
                Helper.share(context, attachment.getFile(context), attachment.getMimeType(), title);
            } catch (Throwable ex) {
                Log.unexpectedError(parentFragment.getParentFragmentManager(), ex);
            }
        }

        private void onDownload(EntityAttachment attachment) {
            if (properties != null)
                properties.setValue("attachment", Long.toString(attachment.id));

            Bundle args = new Bundle();
            args.putLong("id", attachment.id);
            args.putLong("message", attachment.message);
            args.putInt("sequence", attachment.sequence);

            new SimpleTask<Void>() {
                @Override
                protected Void onExecute(Context context, Bundle args) {
                    long id = args.getLong("id");
                    long mid = args.getLong("message");

                    Long reload = null;

                    DB db = DB.getInstance(context);
                    try {
                        db.beginTransaction();

                        EntityMessage message = db.message().getMessage(mid);
                        if (message == null)
                            return null;

                        EntityAccount account = db.account().getAccount(message.account);
                        if (account == null)
                            return null;

                        if (account.protocol == EntityAccount.TYPE_IMAP && message.uid == null)
                            return null;

                        if (!"connected".equals(account.state) && !account.isTransient(context))
                            reload = account.id;

                        EntityAttachment attachment = db.attachment().getAttachment(id);
                        if (attachment == null || attachment.progress != null || attachment.available)
                            return null;

                        EntityOperation.queue(context, message, EntityOperation.ATTACHMENT, id);

                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }

                    if (reload == null)
                        ServiceSynchronize.eval(context, "attachment");
                    else
                        ServiceSynchronize.reload(context, reload, true, "attachment");

                    return null;
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Log.unexpectedError(parentFragment.getParentFragmentManager(), ex);
                }
            }.execute(context, owner, args, "attachment:fetch");
        }
    }

    AdapterAttachment(Fragment parentFragment, boolean readonly, final AdapterMessage.IProperties properties) {
        this.parentFragment = parentFragment;
        this.readonly = readonly;
        this.properties = properties;

        this.context = parentFragment.getContext();
        this.owner = parentFragment.getViewLifecycleOwner();
        this.inflater = LayoutInflater.from(context);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.vt_enabled = prefs.getBoolean("vt_enabled", false);
        this.vt_apikey = prefs.getString("vt_apikey", null);
        this.debug = prefs.getBoolean("debug", false);
        this.dp12 = Helper.dp2pixels(context, 12);
        this.dp36 = Helper.dp2pixels(context, 36);
        this.textColorTertiary = Helper.resolveColor(context, android.R.attr.textColorTertiary);
        this.colorWarning = Helper.resolveColor(context, R.attr.colorWarning);

        setHasStableIds(true);

        owner.getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            public void onDestroyed() {
                Log.d(AdapterAttachment.this + " parent destroyed");
                AdapterAttachment.this.parentFragment = null;
                owner.getLifecycle().removeObserver(this);
            }
        });
    }

    public void set(@NonNull List<EntityAttachment> attachments) {
        Log.i("Set attachments=" + attachments.size());

        Collections.sort(attachments, new Comparator<EntityAttachment>() {
            @Override
            public int compare(EntityAttachment a1, EntityAttachment a2) {
                return a1.sequence.compareTo(a2.sequence);
            }
        });

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffCallback(items, attachments), false);

        items = attachments;

        diff.dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
                Log.d("Inserted @" + position + " #" + count);
            }

            @Override
            public void onRemoved(int position, int count) {
                Log.d("Removed @" + position + " #" + count);
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {
                Log.d("Moved " + fromPosition + ">" + toPosition);
            }

            @Override
            public void onChanged(int position, int count, Object payload) {
                Log.d("Changed @" + position + " #" + count);
            }
        });

        try {
            diff.dispatchUpdatesTo(this);
        } catch (Throwable ex) {
            Log.e(ex);
        }
    }

    private static class DiffCallback extends DiffUtil.Callback {
        private List<EntityAttachment> prev = new ArrayList<>();
        private List<EntityAttachment> next = new ArrayList<>();

        DiffCallback(List<EntityAttachment> prev, List<EntityAttachment> next) {
            this.prev.addAll(prev);
            this.next.addAll(next);
        }

        @Override
        public int getOldListSize() {
            return prev.size();
        }

        @Override
        public int getNewListSize() {
            return next.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            EntityAttachment a1 = prev.get(oldItemPosition);
            EntityAttachment a2 = next.get(newItemPosition);
            return a1.id.equals(a2.id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            EntityAttachment a1 = prev.get(oldItemPosition);
            EntityAttachment a2 = next.get(newItemPosition);
            return a1.equals(a2);
        }
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.item_attachment, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.unwire();

        EntityAttachment attachment = items.get(position);
        holder.powner.recreate(attachment == null ? null : attachment.id);
        holder.bindTo(attachment);

        holder.wire();
    }
}
