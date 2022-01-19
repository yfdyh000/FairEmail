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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity(
        tableName = EntityAccount.TABLE_NAME,
        indices = {
                @Index(value = {"synchronize"}),
                @Index(value = {"category"})
        }
)
public class EntityAccount extends EntityOrder implements Serializable {
    static final String TABLE_NAME = "account";

    // https://tools.ietf.org/html/rfc2177
    static final int DEFAULT_KEEP_ALIVE_INTERVAL = 15; // minutes
    static final int DEFAULT_POLL_INTERVAL = 15; // minutes
    static final int DEFAULT_MAX_MESSAGES = 250; // POP3

    static final int TYPE_IMAP = 0;
    static final int TYPE_POP = 1;

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull
    public String uuid = UUID.randomUUID().toString();

    @NonNull
    @ColumnInfo(name = "pop")
    public Integer protocol = TYPE_IMAP;
    @NonNull
    public String host; // POP3/IMAP
    @NonNull
    @ColumnInfo(name = "starttls")
    public Integer encryption;
    @NonNull
    public Boolean insecure = false;
    @NonNull
    public Integer port;
    @NonNull
    public Integer auth_type; // immutable
    public String provider;
    @NonNull
    public String user;
    @NonNull
    public String password;
    @NonNull
    public Boolean certificate = false; // obsolete
    public String certificate_alias;
    public String realm;
    public String fingerprint;

    public Integer ipPolicy;
    public String ipPool;

    public String name;
    public String category;
    public String signature; // obsolete
    public Integer color;

    @NonNull
    public Boolean synchronize;
    @NonNull
    public Boolean ondemand = false;
    @NonNull
    public Boolean poll_exempted = false;
    @NonNull
    public Boolean primary;
    @NonNull
    public Boolean notify = false;
    @NonNull
    public Boolean browse = true;
    @NonNull
    public Boolean leave_on_server = true;
    @NonNull
    public Boolean leave_deleted = false;
    @NonNull
    public Boolean leave_on_device = false;
    public Integer max_messages; // POP3
    @NonNull
    public Boolean auto_seen = true;
    @ColumnInfo(name = "separator")
    public Character _separator; // obsolete
    public Long swipe_left;
    public Long swipe_right;
    public Long move_to;
    @NonNull
    public Integer poll_interval = DEFAULT_KEEP_ALIVE_INTERVAL;
    @NonNull
    public Boolean keep_alive_ok = false;
    @NonNull
    public Integer keep_alive_failed = 0;
    @NonNull
    public Integer keep_alive_succeeded = 0;
    @NonNull
    public Boolean partial_fetch = true;
    @NonNull
    public Boolean ignore_size = false;
    @NonNull
    public Boolean use_date = false; // Date header
    @NonNull
    public Boolean use_received = false; // Received header
    public String prefix; // namespace, obsolete

    public Long quota_usage;
    public Long quota_limit;

    public Long created = 0L;
    public Boolean tbd;
    public Long thread;
    public String state;
    public String warning;
    public String error;
    public Long last_connected;
    public Long backoff_until;
    public Long max_size;
    public String capabilities;
    public Boolean capability_idle;
    public Boolean capability_utf8;

    boolean isGmail() {
        return "imap.gmail.com".equalsIgnoreCase(host);
    }

    boolean isOutlook() {
        return "outlook.office365.com".equalsIgnoreCase(host);
    }

    boolean isYahooJp() {
        return "imap.mail.yahoo.co.jp".equalsIgnoreCase(host);
    }

    boolean isSeznam() {
        return "imap.seznam.cz".equalsIgnoreCase(host);
    }

    boolean isZoho() {
        return (host != null && host.toLowerCase(Locale.ROOT).startsWith("imap.zoho."));
    }

    boolean isTransient(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enabled = prefs.getBoolean("enabled", true);
        int pollInterval = ServiceSynchronize.getPollInterval(context);
        return (!enabled || this.ondemand || (pollInterval > 0 && !isExempted(context)));
    }

    boolean isExempted(Context context) {
        return (!Helper.isOptimizing12(context) && this.poll_exempted);
    }

    String getProtocol() {
        switch (protocol) {
            case TYPE_IMAP:
                if (isGmail())
                    return "gimaps";
                else
                    return "imap" + (encryption == EmailService.ENCRYPTION_SSL ? "s" : "");
            case TYPE_POP:
                return "pop3" + (encryption == EmailService.ENCRYPTION_SSL ? "s" : "");
            default:
                throw new IllegalArgumentException("Unknown protocol=" + protocol);
        }
    }

    static String getNotificationChannelId(long id) {
        return "notification" + (id == 0 ? "" : "." + id);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    void createNotificationChannel(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannelGroup group = new NotificationChannelGroup("group." + id, name);
        nm.createNotificationChannelGroup(group);

        NotificationChannel channel = new NotificationChannel(
                getNotificationChannelId(id), name,
                NotificationManager.IMPORTANCE_HIGH);
        channel.setGroup(group.getId());
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        channel.setBypassDnd(true);
        channel.enableLights(true);
        nm.createNotificationChannel(channel);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    void deleteNotificationChannel(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.deleteNotificationChannel(getNotificationChannelId(id));
    }

    @Override
    Long getSortId() {
        return id;
    }

    Integer getQuotaPercentage() {
        if (quota_usage == null || quota_limit == null)
            return null;

        int percent = Math.round(quota_usage * 100f / quota_limit);
        return (percent > 100 ? null : percent);
    }

    @Override
    String[] getSortTitle(Context context) {
        return new String[]{name, null};
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("uuid", uuid);
        json.put("order", order);
        json.put("protocol", protocol);
        json.put("host", host);
        json.put("encryption", encryption);
        json.put("insecure", insecure);
        json.put("port", port);
        json.put("auth_type", auth_type);
        json.put("provider", provider);
        json.put("user", user);
        json.put("password", password);
        json.put("certificate_alias", certificate_alias);
        json.put("realm", realm);
        json.put("fingerprint", fingerprint);

        json.put("name", name);
        json.put("category", category);
        json.put("color", color);

        json.put("synchronize", synchronize);
        json.put("ondemand", ondemand);
        json.put("poll_exempted", poll_exempted);
        json.put("primary", primary);
        json.put("notify", notify);
        json.put("browse", browse);
        json.put("leave_on_server", leave_on_server);
        json.put("leave_deleted", leave_deleted);
        json.put("leave_on_device", leave_on_device);
        json.put("max_messages", max_messages);
        json.put("auto_seen", auto_seen);
        // not separator

        json.put("swipe_left", swipe_left);
        json.put("swipe_right", swipe_right);

        json.put("move_to", move_to);

        json.put("poll_interval", poll_interval);
        json.put("partial_fetch", partial_fetch);
        json.put("ignore_size", ignore_size);
        json.put("use_date", use_date);
        json.put("use_received", use_received);
        // not prefix
        // not created
        // not tbd
        // not state
        // not warning
        // not error
        // not last connected
        return json;
    }

    public static EntityAccount fromJSON(JSONObject json) throws JSONException {
        EntityAccount account = new EntityAccount();
        if (json.has("id"))
            account.id = json.getLong("id");

        if (json.has("uuid"))
            account.uuid = json.getString("uuid");

        if (json.has("order"))
            account.order = json.getInt("order");

        if (json.has("protocol"))
            account.protocol = json.getInt("protocol");
        else if (json.has("pop"))
            account.protocol = (json.getBoolean("pop") ? TYPE_POP : TYPE_IMAP);

        account.host = json.getString("host");
        if (json.has("starttls"))
            account.encryption = (json.getBoolean("starttls")
                    ? EmailService.ENCRYPTION_STARTTLS : EmailService.ENCRYPTION_SSL);
        else
            account.encryption = json.getInt("encryption");
        account.insecure = (json.has("insecure") && json.getBoolean("insecure"));
        account.port = json.getInt("port");
        account.auth_type = json.getInt("auth_type");
        if (json.has("provider"))
            account.provider = json.getString("provider");
        account.user = json.getString("user");
        account.password = json.getString("password");
        if (json.has("certificate_alias"))
            account.certificate_alias = json.getString("certificate_alias");
        if (json.has("realm"))
            account.realm = json.getString("realm");
        if (json.has("fingerprint"))
            account.fingerprint = json.getString("fingerprint");

        if (json.has("name") && !json.isNull("name"))
            account.name = json.getString("name");
        if (json.has("category") && !json.isNull("category"))
            account.category = json.getString("category");
        if (json.has("color"))
            account.color = json.getInt("color");

        account.synchronize = json.getBoolean("synchronize");
        if (json.has("ondemand"))
            account.ondemand = json.getBoolean("ondemand");
        if (json.has("poll_exempted"))
            account.poll_exempted = json.getBoolean("poll_exempted");
        account.primary = json.getBoolean("primary");
        if (json.has("notify"))
            account.notify = json.getBoolean("notify");
        if (json.has("browse"))
            account.browse = json.getBoolean("browse");
        if (json.has("leave_on_server"))
            account.leave_on_server = json.getBoolean("leave_on_server");
        if (json.has("leave_deleted"))
            account.leave_deleted = json.getBoolean("leave_deleted");
        if (json.has("leave_on_device"))
            account.leave_on_device = json.getBoolean("leave_on_device");
        if (json.has("max_messages"))
            account.max_messages = json.getInt("max_messages");
        if (json.has("auto_seen"))
            account.auto_seen = json.getBoolean("auto_seen");

        if (json.has("swipe_left"))
            account.swipe_left = json.getLong("swipe_left");
        if (json.has("swipe_right"))
            account.swipe_right = json.getLong("swipe_right");

        if (json.has("move_to"))
            account.move_to = json.getLong("move_to");

        account.poll_interval = json.getInt("poll_interval");

        account.partial_fetch = json.optBoolean("partial_fetch", true);
        account.ignore_size = json.optBoolean("ignore_size", false);
        account.use_date = json.optBoolean("use_date", false);
        account.use_received = json.optBoolean("use_received", false);

        return account;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EntityAccount) {
            EntityAccount other = (EntityAccount) obj;
            return (Objects.equals(this.uuid, other.uuid) &&
                    Objects.equals(this.order, other.order) &&
                    this.protocol.equals(other.protocol) &&
                    this.host.equals(other.host) &&
                    this.encryption.equals(other.encryption) &&
                    this.insecure == other.insecure &&
                    this.port.equals(other.port) &&
                    this.auth_type.equals(other.auth_type) &&
                    this.user.equals(other.user) &&
                    this.password.equals(other.password) &&
                    Objects.equals(this.realm, other.realm) &&
                    Objects.equals(this.name, other.name) &&
                    Objects.equals(this.category, other.category) &&
                    Objects.equals(this.color, other.color) &&
                    this.synchronize.equals(other.synchronize) &&
                    this.primary.equals(other.primary) &&
                    this.notify.equals(other.notify) &&
                    this.browse.equals(other.browse) &&
                    this.leave_on_server.equals(other.leave_on_server) &&
                    this.leave_on_device.equals(other.leave_on_device) &&
                    Objects.equals(this.max_messages, other.max_messages) &&
                    this.auto_seen.equals(other.auto_seen) &&
                    Objects.equals(this.swipe_left, other.swipe_left) &&
                    Objects.equals(this.swipe_right, other.swipe_right) &&
                    this.poll_interval.equals(other.poll_interval) &&
                    this.partial_fetch == other.partial_fetch &&
                    this.ignore_size == other.ignore_size &&
                    this.use_date == other.use_date &&
                    this.use_received == other.use_received &&
                    Objects.equals(this.quota_usage, other.quota_usage) &&
                    Objects.equals(this.quota_limit, other.quota_limit) &&
                    Objects.equals(this.created, other.created) &&
                    Objects.equals(this.tbd, other.tbd) &&
                    Objects.equals(this.state, other.state) &&
                    Objects.equals(this.warning, other.warning) &&
                    Objects.equals(this.error, other.error) &&
                    Objects.equals(this.last_connected, other.last_connected) &&
                    Objects.equals(this.backoff_until, other.backoff_until) &&
                    Objects.equals(this.max_size, other.max_size) &&
                    Objects.equals(this.capabilities, other.capabilities) &&
                    Objects.equals(this.capability_idle, other.capability_idle) &&
                    Objects.equals(this.capability_utf8, other.capability_utf8));
        } else
            return false;
    }

    @Override
    Comparator getComparator(final Context context) {
        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc

        return new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                EntityAccount a1 = (EntityAccount) o1;
                EntityAccount a2 = (EntityAccount) o2;

                int o = Integer.compare(
                        a1.order == null ? -1 : a1.order,
                        a2.order == null ? -1 : a2.order);
                if (o != 0)
                    return o;

                String name1 = (a1.name == null ? "" : a1.name);
                String name2 = (a2.name == null ? "" : a2.name);
                return collator.compare(name1, name2);
            }
        };
    }

    @NonNull
    @Override
    public String toString() {
        return name + (primary ? " ★" : "");
    }
}
