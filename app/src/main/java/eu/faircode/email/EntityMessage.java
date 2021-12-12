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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;

import static androidx.room.ForeignKey.CASCADE;
import static androidx.room.ForeignKey.SET_NULL;

// https://developer.android.com/training/data-storage/room/defining-data

@Entity(
        tableName = EntityMessage.TABLE_NAME,
        foreignKeys = {
                @ForeignKey(childColumns = "account", entity = EntityAccount.class, parentColumns = "id", onDelete = CASCADE),
                @ForeignKey(childColumns = "folder", entity = EntityFolder.class, parentColumns = "id", onDelete = CASCADE),
                @ForeignKey(childColumns = "identity", entity = EntityIdentity.class, parentColumns = "id", onDelete = SET_NULL),
                @ForeignKey(childColumns = "replying", entity = EntityMessage.class, parentColumns = "id", onDelete = SET_NULL),
                @ForeignKey(childColumns = "forwarding", entity = EntityMessage.class, parentColumns = "id", onDelete = SET_NULL)
        },
        indices = {
                @Index(value = {"account"}),
                @Index(value = {"folder"}),
                @Index(value = {"identity"}),
                @Index(value = {"folder", "uid"}, unique = true),
                @Index(value = {"inreplyto"}),
                @Index(value = {"msgid"}),
                @Index(value = {"thread"}),
                @Index(value = {"sender"}),
                @Index(value = {"received"}),
                @Index(value = {"subject"}),
                @Index(value = {"ui_seen"}),
                @Index(value = {"ui_flagged"}),
                @Index(value = {"ui_hide"}),
                @Index(value = {"ui_found"}),
                @Index(value = {"ui_ignored"}),
                @Index(value = {"ui_browsed"}),
                @Index(value = {"ui_snoozed"})
        }
)
public class EntityMessage implements Serializable {
    static final String TABLE_NAME = "message";

    static final int NOTIFYING_IGNORE = -2;

    static final Integer ENCRYPT_NONE = 0;
    static final Integer PGP_SIGNENCRYPT = 1;
    static final Integer PGP_SIGNONLY = 2;
    static final Integer SMIME_SIGNENCRYPT = 3;
    static final Integer SMIME_SIGNONLY = 4;

    static final Integer PRIORITIY_LOW = 0;
    static final Integer PRIORITIY_NORMAL = 1;
    static final Integer PRIORITIY_HIGH = 2;

    static final Integer DSN_NONE = 0;
    static final Integer DSN_RECEIPT = 1;
    static final Integer DSN_HARD_BOUNCE = 2;

    static final Long SWIPE_ACTION_ASK = -1L;
    static final Long SWIPE_ACTION_SEEN = -2L;
    static final Long SWIPE_ACTION_SNOOZE = -3L;
    static final Long SWIPE_ACTION_HIDE = -4L;
    static final Long SWIPE_ACTION_MOVE = -5L;
    static final Long SWIPE_ACTION_FLAG = -6L;
    static final Long SWIPE_ACTION_DELETE = -7L;
    static final Long SWIPE_ACTION_JUNK = -8L;
    static final Long SWIPE_ACTION_REPLY = -9L;

    @PrimaryKey(autoGenerate = true)
    public Long id;
    @NonNull
    public Long account; // performance
    @NonNull
    public Long folder;
    public Long identity;
    public String extra; // plus
    public Long replying; // obsolete
    public Long forwarding; // obsolete
    public Long uid; // compose/moved = null
    public String uidl; // POP3
    public String msgid;
    public String hash; // headers hash
    public String references;
    public String deliveredto;
    public String inreplyto;
    public String wasforwardedfrom;
    public String thread; // compose = null
    public Integer priority;
    public Integer importance;
    public Boolean auto_submitted;
    @ColumnInfo(name = "receipt")
    public Integer dsn;
    public Boolean receipt_request;
    public Address[] receipt_to;
    public String bimi_selector;
    public Boolean dkim;
    public Boolean spf;
    public Boolean dmarc;
    public Boolean mx;
    public Boolean blocklist;
    public Boolean from_domain; // spf/smtp.mailfrom <> from
    public Boolean reply_domain; // reply-to <> from
    public String avatar; // lookup URI from sender
    public String sender; // sort key: from email address
    public Address[] return_path;
    public Address[] smtp_from;
    public Address[] submitter; // sent on behalf of
    public Address[] from;
    public Address[] to;
    public Address[] cc;
    public Address[] bcc;
    public Address[] reply;
    public Address[] list_post;
    public String unsubscribe;
    public String autocrypt;
    public String headers;
    public String infrastructure;
    public Boolean raw;
    public String subject;
    public Long size;
    public Long total;
    @NonNull
    public Integer attachments = 0; // performance
    @NonNull
    public Boolean content = false;
    public String language = null; // classified
    public Boolean plain_only = null;
    public Integer encrypt = null;
    public Integer ui_encrypt = null;
    @NonNull
    public Boolean verified = false;
    public String preview;
    public String notes;
    public Integer notes_color;
    @NonNull
    public Boolean signature = true;
    public Long sent; // compose = null
    @NonNull
    public Long received; // compose = stored
    @NonNull
    public Long stored = new Date().getTime();
    @NonNull
    public Boolean seen = false;
    @NonNull
    public Boolean answered = false;
    @NonNull
    public Boolean flagged = false;
    @NonNull
    public Boolean deleted = false;
    public String flags; // system flags
    public String[] keywords; // user flags
    public String[] labels; // Gmail
    @NonNull
    public Boolean fts = false;
    @NonNull
    public Boolean auto_classified = false;
    @NonNull
    public Integer notifying = 0;
    @NonNull
    public Boolean ui_seen = false;
    @NonNull
    public Boolean ui_answered = false;
    @NonNull
    public Boolean ui_flagged = false;
    @NonNull
    public Boolean ui_deleted = false;
    @NonNull
    public Boolean ui_hide = false;
    @NonNull
    public Boolean ui_found = false;
    @NonNull
    public Boolean ui_ignored = false;
    @NonNull
    public Boolean ui_silent = false;
    @NonNull
    public Boolean ui_browsed = false;
    public Long ui_busy;
    public Long ui_snoozed;
    @NonNull
    public Boolean ui_unsnoozed = false;
    @NonNull
    public Boolean show_images = false;
    @NonNull
    public Boolean show_full = false;
    public Integer color;
    public Integer revision; // compose
    public Integer revisions; // compose
    public String warning; // persistent
    public String error; // volatile
    public Long last_attempt; // send

    static String generateMessageId() {
        return generateMessageId("localhost");
    }

    static String generateMessageId(String domain) {
        // https://www.jwz.org/doc/mid.html
        // https://tools.ietf.org/html/rfc2822.html#section-3.6.4
        return "<" + UUID.randomUUID() + "@" + domain + '>';
    }

    boolean replySelf(List<TupleIdentityEx> identities, long account) {
        Address[] senders = (reply == null || reply.length == 0 ? from : reply);
        if (identities != null && senders != null)
            for (Address sender : senders)
                for (TupleIdentityEx identity : identities)
                    if (identity.account == account &&
                            identity.self &&
                            identity.similarAddress(sender))
                        return true;

        return false;
    }

    Address[] getAllRecipients(List<TupleIdentityEx> identities, long account) {
        List<Address> addresses = new ArrayList<>();

        if (!replySelf(identities, account)) {
            if (to != null)
                addresses.addAll(Arrays.asList(to));
        }

        if (cc != null)
            addresses.addAll(Arrays.asList(cc));

        // Filter from
        if (from != null)
            for (Address address : new ArrayList<>(addresses))
                for (Address f : from)
                    if (MessageHelper.equalEmail(address, f)) {
                        addresses.remove(address);
                        break;
                    }

        // Filter self
        if (identities != null)
            for (Address address : new ArrayList<>(addresses))
                for (TupleIdentityEx identity : identities)
                    if (identity.account == account &&
                            identity.self &&
                            identity.similarAddress(address))
                        addresses.remove(address);

        return addresses.toArray(new Address[0]);
    }

    boolean hasKeyword(@NonNull String value) {
        // https://tools.ietf.org/html/rfc5788
        if (keywords == null)
            return false;

        for (String keyword : keywords)
            if (value.equalsIgnoreCase(keyword))
                return true;

        return false;
    }

    boolean isForwarded() {
        return hasKeyword(MessageHelper.FLAG_FORWARDED);
    }

    String[] checkFromDomain(Context context) {
        return MessageHelper.equalDomain(context, from, smtp_from);
    }

    String[] checkReplyDomain(Context context) {
        return MessageHelper.equalDomain(context, reply, from);
    }

    static String collapsePrefixes(Context context, String language, String subject, boolean forward) {
        List<Pair<String, Boolean>> prefixes = new ArrayList<>();
        for (String re : Helper.getStrings(context, language, R.string.title_subject_reply, ""))
            prefixes.add(new Pair<>(re.trim().toLowerCase(), false));
        for (String re : Helper.getStrings(context, language, R.string.title_subject_reply_alt, ""))
            prefixes.add(new Pair<>(re.trim().toLowerCase(), false));
        for (String fwd : Helper.getStrings(context, language, R.string.title_subject_forward, ""))
            prefixes.add(new Pair<>(fwd.trim().toLowerCase(), true));
        for (String fwd : Helper.getStrings(context, language, R.string.title_subject_forward_alt, ""))
            prefixes.add(new Pair<>(fwd.trim().toLowerCase(), true));

        List<Boolean> scanned = new ArrayList<>();
        subject = subject.trim();
        while (true) {
            boolean found = false;
            for (Pair<String, Boolean> prefix : prefixes)
                if (subject.toLowerCase().startsWith(prefix.first)) {
                    found = true;
                    int count = scanned.size();
                    if (!prefix.second.equals(count == 0 ? forward : scanned.get(count - 1)))
                        scanned.add(prefix.second);
                    subject = subject.substring(prefix.first.length()).trim();
                    break;
                }
            if (!found)
                break;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < scanned.size(); i++)
            result.append(context.getString(scanned.get(i) ? R.string.title_subject_forward : R.string.title_subject_reply, ""));
        result.append(subject);

        return result.toString();
    }

    Element getReplyHeader(Context context, Document document, boolean separate, boolean extended) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean language_detection = prefs.getBoolean("language_detection", false);
        String l = (language_detection ? language : null);

        DateFormat DF = Helper.getDateTimeInstance(context);

        Element p = document.createElement("p");
        if (extended) {
            if (from != null && from.length > 0) {
                Element strong = document.createElement("strong");
                strong.text(Helper.getString(context, l, R.string.title_from) + " ");
                p.appendChild(strong);
                p.appendText(MessageHelper.formatAddresses(from));
                p.appendElement("br");
            }
            if (to != null && to.length > 0) {
                Element strong = document.createElement("strong");
                strong.text(Helper.getString(context, l, R.string.title_to) + " ");
                p.appendChild(strong);
                p.appendText(MessageHelper.formatAddresses(to));
                p.appendElement("br");
            }
            if (cc != null && cc.length > 0) {
                Element strong = document.createElement("strong");
                strong.text(Helper.getString(context, l, R.string.title_cc) + " ");
                p.appendChild(strong);
                p.appendText(MessageHelper.formatAddresses(cc));
                p.appendElement("br");
            }
            if (received != null) { // embedded messages
                Element strong = document.createElement("strong");
                strong.text(Helper.getString(context, l, R.string.title_date) + " ");
                p.appendChild(strong);
                p.appendText(DF.format(received));
                p.appendElement("br");
            }
            if (!TextUtils.isEmpty(subject)) {
                Element strong = document.createElement("strong");
                strong.text(Helper.getString(context, l, R.string.title_subject) + " ");
                p.appendChild(strong);
                p.appendText(subject);
                p.appendElement("br");
            }
        } else
            p.text(DF.format(new Date(received)) + " " + MessageHelper.formatAddresses(from) + ":");

        Element div = document.createElement("div")
                .attr("fairemail", "reply");
        if (separate)
            div.appendElement("hr");
        div.appendChild(p);
        return div;
    }

    String getNotificationChannelId() {
        if (from == null || from.length == 0)
            return null;
        InternetAddress sender = (InternetAddress) from[0];
        return "notification." + sender.getAddress().toLowerCase(Locale.ROOT);
    }

    boolean setLabel(String label, boolean set) {
        List<String> list = new ArrayList<>();
        if (labels != null)
            list.addAll(Arrays.asList(labels));

        boolean changed = false;
        if (set) {
            if (!list.contains(label)) {
                changed = true;
                list.add(label);
            }
        } else {
            if (list.contains(label)) {
                changed = true;
                list.remove(label);
            }
        }

        if (changed)
            labels = list.toArray(new String[0]);

        return changed;
    }

    static File getFile(Context context, Long id) {
        File dir = new File(context.getFilesDir(), "messages");
        if (!dir.exists())
            dir.mkdir();
        return new File(dir, id.toString());
    }

    File getFile(Context context) {
        return getFile(context, id);
    }

    File getFile(Context context, int revision) {
        File dir = new File(context.getFilesDir(), "revision");
        if (!dir.exists())
            dir.mkdir();
        return new File(dir, id + "." + revision);
    }

    File getRefFile(Context context) {
        File dir = new File(context.getFilesDir(), "references");
        if (!dir.exists())
            dir.mkdir();
        return new File(dir, id.toString());
    }

    File getRawFile(Context context) {
        return getRawFile(context, id);
    }

    static File getRawFile(Context context, Long id) {
        File dir = new File(context.getFilesDir(), "raw");
        if (!dir.exists())
            dir.mkdir();
        return new File(dir, id + ".eml");
    }

    static void snooze(Context context, long id, Long wakeup) {
        Intent snoozed = new Intent(context, ServiceSynchronize.class);
        snoozed.setAction("unsnooze:" + id);
        PendingIntent pi = PendingIntentCompat.getForegroundService(
                context, ServiceSynchronize.PI_UNSNOOZE, snoozed, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (wakeup == null || wakeup == Long.MAX_VALUE) {
            Log.i("Cancel snooze id=" + id);
            am.cancel(pi);
        } else {
            Log.i("Set snooze id=" + id + " wakeup=" + new Date(wakeup));
            AlarmManagerCompatEx.setAndAllowWhileIdle(context, am, AlarmManager.RTC_WAKEUP, wakeup, pi);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EntityMessage) {
            EntityMessage other = (EntityMessage) obj;
            return (Objects.equals(this.account, other.account) &&
                    this.folder.equals(other.folder) &&
                    Objects.equals(this.identity, other.identity) &&
                    // extra
                    Objects.equals(this.uid, other.uid) &&
                    Objects.equals(this.msgid, other.msgid) &&
                    Objects.equals(this.references, other.references) &&
                    Objects.equals(this.deliveredto, other.deliveredto) &&
                    Objects.equals(this.inreplyto, other.inreplyto) &&
                    Objects.equals(this.wasforwardedfrom, other.wasforwardedfrom) &&
                    Objects.equals(this.thread, other.thread) &&
                    Objects.equals(this.priority, other.priority) &&
                    Objects.equals(this.dsn, other.dsn) &&
                    Objects.equals(this.receipt_request, other.receipt_request) &&
                    MessageHelper.equal(this.receipt_to, other.receipt_to) &&
                    Objects.equals(this.bimi_selector, other.bimi_selector) &&
                    Objects.equals(this.dkim, other.dkim) &&
                    Objects.equals(this.spf, other.spf) &&
                    Objects.equals(this.dmarc, other.dmarc) &&
                    Objects.equals(this.mx, other.mx) &&
                    Objects.equals(this.blocklist, other.blocklist) &&
                    Objects.equals(this.from_domain, other.from_domain) &&
                    Objects.equals(this.reply_domain, other.reply_domain) &&
                    Objects.equals(this.avatar, other.avatar) &&
                    Objects.equals(this.sender, other.sender) &&
                    MessageHelper.equal(this.return_path, other.return_path) &&
                    MessageHelper.equal(this.smtp_from, other.smtp_from) &&
                    MessageHelper.equal(this.submitter, other.submitter) &&
                    MessageHelper.equal(this.from, other.from) &&
                    MessageHelper.equal(this.to, other.to) &&
                    MessageHelper.equal(this.cc, other.cc) &&
                    MessageHelper.equal(this.bcc, other.bcc) &&
                    MessageHelper.equal(this.reply, other.reply) &&
                    MessageHelper.equal(this.list_post, other.list_post) &&
                    Objects.equals(this.unsubscribe, other.unsubscribe) &&
                    Objects.equals(this.autocrypt, other.autocrypt) &&
                    Objects.equals(this.headers, other.headers) &&
                    Objects.equals(this.infrastructure, other.infrastructure) &&
                    Objects.equals(this.raw, other.raw) &&
                    Objects.equals(this.subject, other.subject) &&
                    Objects.equals(this.size, other.size) &&
                    Objects.equals(this.total, other.total) &&
                    Objects.equals(this.attachments, other.attachments) &&
                    this.content == other.content &&
                    Objects.equals(this.language, other.language) &&
                    Objects.equals(this.plain_only, other.plain_only) &&
                    Objects.equals(this.encrypt, other.encrypt) &&
                    Objects.equals(this.ui_encrypt, other.ui_encrypt) &&
                    this.verified == other.verified &&
                    Objects.equals(this.preview, other.preview) &&
                    Objects.equals(this.notes, other.notes) &&
                    Objects.equals(this.notes_color, other.notes_color) &&
                    this.signature.equals(other.signature) &&
                    Objects.equals(this.sent, other.sent) &&
                    this.received.equals(other.received) &&
                    this.stored.equals(other.stored) &&
                    this.seen.equals(other.seen) &&
                    this.answered.equals(other.answered) &&
                    this.flagged.equals(other.flagged) &&
                    this.deleted.equals(other.deleted) &&
                    Objects.equals(this.flags, other.flags) &&
                    Helper.equal(this.keywords, other.keywords) &&
                    this.auto_classified.equals(other.auto_classified) &&
                    this.notifying.equals(other.notifying) &&
                    this.ui_seen.equals(other.ui_seen) &&
                    this.ui_answered.equals(other.ui_answered) &&
                    this.ui_flagged.equals(other.ui_flagged) &&
                    this.ui_deleted.equals(other.ui_deleted) &&
                    this.ui_hide.equals(other.ui_hide) &&
                    this.ui_found.equals(other.ui_found) &&
                    this.ui_ignored.equals(other.ui_ignored) &&
                    this.ui_silent.equals(other.ui_silent) &&
                    this.ui_browsed.equals(other.ui_browsed) &&
                    Objects.equals(this.ui_busy, other.ui_busy) &&
                    Objects.equals(this.ui_snoozed, other.ui_snoozed) &&
                    this.ui_unsnoozed.equals(other.ui_unsnoozed) &&
                    this.show_images.equals(other.show_images) &&
                    this.show_full.equals(other.show_full) &&
                    Objects.equals(this.color, other.color) &&
                    Objects.equals(this.revision, other.revision) &&
                    Objects.equals(this.revisions, other.revisions) &&
                    Objects.equals(this.warning, other.warning) &&
                    Objects.equals(this.error, other.error) &&
                    Objects.equals(this.last_attempt, other.last_attempt));
        }
        return false;
    }
}
