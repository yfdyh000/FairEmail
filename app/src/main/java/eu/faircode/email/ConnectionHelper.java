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

import android.accounts.AccountsException;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.sun.mail.iap.ConnectionException;
import com.sun.mail.util.FolderClosedIOException;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class ConnectionHelper {
    static final List<String> PREF_NETWORK = Collections.unmodifiableList(Arrays.asList(
            "metered", "roaming", "rlah", "require_validated", "vpn_only" // update network state
    ));

    // Roam like at home
    // https://en.wikipedia.org/wiki/European_Union_roaming_regulations
    private static final List<String> RLAH_COUNTRY_CODES = Collections.unmodifiableList(Arrays.asList(
            "AT", // Austria
            "BE", // Belgium
            "BG", // Bulgaria
            "HR", // Croatia
            "CY", // Cyprus
            "CZ", // Czech Republic
            "DK", // Denmark
            "EE", // Estonia
            "FI", // Finland
            "FR", // France
            "DE", // Germany
            "GR", // Greece
            "HU", // Hungary
            "IS", // Iceland
            "IE", // Ireland
            "IT", // Italy
            "LV", // Latvia
            "LI", // Liechtenstein
            "LT", // Lithuania
            "LU", // Luxembourg
            "MT", // Malta
            "NL", // Netherlands
            "NO", // Norway
            "PL", // Poland
            "PT", // Portugal
            "RE", // La Réunion
            "RO", // Romania
            "SK", // Slovakia
            "SI", // Slovenia
            "ES", // Spain
            "SE" // Sweden
    ));

    static {
        System.loadLibrary("fairemail");
    }

    public static native int jni_socket_keep_alive(int fd, int seconds);

    public static native int jni_socket_get_send_buffer(int fd);

    public static native boolean jni_is_numeric_address(String _ip);

    static class NetworkState {
        private Boolean connected = null;
        private Boolean suitable = null;
        private Boolean unmetered = null;
        private Boolean roaming = null;
        private Network active = null;

        boolean isConnected() {
            return (connected != null && connected);
        }

        boolean isSuitable() {
            return (suitable != null && suitable);
        }

        boolean isUnmetered() {
            return (unmetered != null && unmetered);
        }

        boolean isRoaming() {
            return (roaming != null && roaming);
        }

        Network getActive() {
            return active;
        }

        public void update(NetworkState newState) {
            connected = newState.connected;
            suitable = newState.suitable;
            unmetered = newState.unmetered;
            roaming = newState.roaming;
            active = newState.active;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof NetworkState) {
                NetworkState other = (NetworkState) obj;
                return (Objects.equals(this.connected, other.connected) &&
                        Objects.equals(this.suitable, other.suitable) &&
                        Objects.equals(this.unmetered, other.unmetered) &&
                        Objects.equals(this.roaming, other.roaming) &&
                        Objects.equals(this.active, other.active));
            } else
                return false;
        }

        @Override
        public String toString() {
            return "connected=" + connected +
                    " suitable=" + suitable +
                    " unmetered=" + unmetered +
                    " roaming=" + roaming +
                    " active=" + active;
        }
    }

    static boolean isConnected(Context context, Network network) {
        NetworkInfo ni = getNetworkInfo(context, network);
        return (ni != null && ni.isConnected());
    }

    static NetworkInfo getNetworkInfo(Context context, Network network) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            return (cm == null ? null : cm.getNetworkInfo(network));
        } catch (Throwable ex) {
            Log.e(ex);
            return null;
        }
    }

    static NetworkState getNetworkState(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean metered = prefs.getBoolean("metered", true);
        boolean roaming = prefs.getBoolean("roaming", true);
        boolean rlah = prefs.getBoolean("rlah", true);

        NetworkState state = new NetworkState();
        try {
            Boolean isMetered = isMetered(context);
            state.connected = (isMetered != null);
            state.unmetered = (isMetered != null && !isMetered);
            state.suitable = (isMetered != null && (metered || !isMetered));
            state.active = getActiveNetwork(context);

            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (state.connected && !roaming) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    NetworkInfo ani = (cm == null ? null : cm.getActiveNetworkInfo());
                    if (ani != null)
                        state.roaming = ani.isRoaming();
                } else {
                    Network active = (cm == null ? null : cm.getActiveNetwork());
                    if (active != null) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
                        if (caps != null)
                            state.roaming = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
                    }
                }

                if (state.roaming != null && state.roaming && rlah)
                    try {
                        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                        if (tm != null) {
                            String sim = tm.getSimCountryIso();
                            String network = tm.getNetworkCountryIso();
                            Log.i("Country SIM=" + sim + " network=" + network);
                            if (sim != null && network != null &&
                                    RLAH_COUNTRY_CODES.contains(sim.toUpperCase()) &&
                                    RLAH_COUNTRY_CODES.contains(network.toUpperCase()))
                                state.roaming = false;
                        }
                    } catch (Throwable ex) {
                        Log.w(ex);
                    }
            }
        } catch (Throwable ex) {
            Log.e(ex);
        }

        return state;
    }

    private static Boolean isMetered(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean standalone_vpn = prefs.getBoolean("standalone_vpn", false);
        boolean require_validated = prefs.getBoolean("require_validated", false);
        boolean vpn_only = prefs.getBoolean("vpn_only", false);

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Log.i("isMetered: no connectivity manager");
            return null;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            NetworkInfo ani = cm.getActiveNetworkInfo();
            if (ani == null || !ani.isConnected())
                return null;
            return cm.isActiveNetworkMetered();
        }

        Network active = cm.getActiveNetwork();
        if (active == null) {
            Log.i("isMetered: no active network");
            return null;
        }

        // onLost [... state: SUSPENDED/SUSPENDED ... available: true]
        // onLost [... state: DISCONNECTED/DISCONNECTED ... available: true]
        NetworkInfo ani = cm.getNetworkInfo(active);
        if (ani == null || ani.getState() != NetworkInfo.State.CONNECTED) {
            Log.i("isMetered: no/connected active info ani=" + ani);
            if (ani == null ||
                    ani.getState() != NetworkInfo.State.SUSPENDED ||
                    ani.getType() != ConnectivityManager.TYPE_VPN)
                return null;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        if (caps == null) {
            Log.i("isMetered: active no caps");
            return null; // network unknown
        }

        Log.i("isMetered: active caps=" + caps);

        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                Log.i("isMetered: no internet");
                return null;
            }
            if (require_validated &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                Log.i("isMetered: not validated");
                return null;
            }
        }

        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)) {
            Log.i("isMetered: active restricted");
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)) {
            Log.i("isMetered: active background");
            return null;
        }

        if (vpn_only) {
            boolean vpn = vpnActive(context);
            Log.i("VPN only vpn=" + vpn);
            if (!vpn)
                return null;
        }

        if (standalone_vpn ||
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            // NET_CAPABILITY_NOT_METERED is unreliable on older Android versions
            boolean metered = cm.isActiveNetworkMetered();
            Log.i("isMetered: active not VPN metered=" + metered);
            return metered;
        }

        // VPN: evaluate underlying networks
        Integer transport = null;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            transport = NetworkCapabilities.TRANSPORT_CELLULAR;
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            transport = NetworkCapabilities.TRANSPORT_WIFI;

        boolean underlying = false;
        Network[] networks = cm.getAllNetworks();
        for (Network network : networks) {
            caps = cm.getNetworkCapabilities(network);
            if (caps == null) {
                Log.i("isMetered: no underlying caps");
                continue; // network unknown
            }

            Log.i("isMetered: underlying caps=" + caps);

            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                Log.i("isMetered: underlying no internet");
                continue;
            }

            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)) {
                Log.i("isMetered: underlying restricted");
                continue;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)) {
                Log.i("isMetered: underlying background");
                continue;
            }

            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) &&
                    (transport != null && !caps.hasTransport(transport))) {
                Log.i("isMetered: underlying other transport");
                continue;
            }

            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                underlying = true;
                Log.i("isMetered: underlying is connected");

                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                    Log.i("isMetered: underlying is unmetered");
                    return false;
                }
            }
        }

        if (!underlying)
            return null;

        // Assume metered
        Log.i("isMetered: underlying assume metered");
        return true;
    }

    static Network getActiveNetwork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null)
            return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return cm.getActiveNetwork();

        NetworkInfo ani = cm.getActiveNetworkInfo();
        if (ani == null)
            return null;

        Network[] networks = cm.getAllNetworks();
        for (Network network : networks) {
            NetworkInfo ni = cm.getNetworkInfo(network);
            if (ni == null)
                continue;
            if (ni.getType() == ani.getType() &&
                    ni.getSubtype() == ani.getSubtype())
                return network;
        }

        return null;
    }

    static boolean isIoError(Throwable ex) {
        while (ex != null) {
            if (isMaxConnections(ex.getMessage()) ||
                    ex instanceof IOException ||
                    ex instanceof FolderClosedIOException ||
                    ex instanceof ConnectionException ||
                    ex instanceof AccountsException ||
                    ex instanceof InterruptedException ||
                    "EOF on socket".equals(ex.getMessage()) ||
                    "Read timed out".equals(ex.getMessage()) || // POP3
                    "failed to connect".equals(ex.getMessage()))
                return true;
            ex = ex.getCause();
        }
        return false;
    }

    static boolean isMaxConnections(Throwable ex) {
        while (ex != null) {
            if (isMaxConnections(ex.getMessage()))
                return true;
            ex = ex.getCause();
        }
        return false;
    }

    static boolean isMaxConnections(String message) {
        return (message != null &&
                (message.contains("Too many simultaneous connections") /* Gmail */ ||
                        message.contains("Maximum number of connections") /* ... from user+IP exceeded */ /* Dovecot */ ||
                        message.contains("Too many concurrent connections") /* ... to this mailbox */ ||
                        message.contains("User is authenticated but not connected") /* Outlook */ ||
                        message.contains("Account is temporarily unavailable") /* Arcor.de / TalkTalk.net */ ||
                        message.contains("Connection dropped by server?")));
    }

    static Boolean isSyntacticallyInvalid(Throwable ex) {
        if (ex.getMessage() == null)
            return false;
        // 501 HELO requires valid address
        // 501 Syntactically invalid HELO argument(s)
        String message = ex.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("syntactically invalid") ||
                message.contains("requires valid address");
    }

    static boolean isDataSaving(Context context) {
        // https://developer.android.com/training/basics/network-ops/data-saver.html
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            return false;

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null)
            return false;

        // RESTRICT_BACKGROUND_STATUS_DISABLED: Data Saver is disabled.
        // RESTRICT_BACKGROUND_STATUS_ENABLED: The user has enabled Data Saver for this app. (Globally)
        // RESTRICT_BACKGROUND_STATUS_WHITELISTED: The user has enabled Data Saver but the app is allowed to bypass it.
        int status = cm.getRestrictBackgroundStatus();
        return (status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED);
    }

    static boolean vpnActive(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null)
            return false;

        try {
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                    return true;
            }
        } catch (Throwable ex) {
            Log.w(ex);
        }

        return false;
    }

    static boolean airplaneMode(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    static InetAddress from6to4(InetAddress addr) {
        // https://en.wikipedia.org/wiki/6to4
        if (addr instanceof Inet6Address) {
            byte[] octets = ((Inet6Address) addr).getAddress();
            if (octets[0] == 0x20 && octets[1] == 0x02)
                try {
                    return Inet4Address.getByAddress(Arrays.copyOfRange(octets, 2, 6));
                } catch (Throwable ex) {
                    Log.e(ex);
                }
        }
        return addr;
    }

    static boolean isNumericAddress(String host) {
        return ConnectionHelper.jni_is_numeric_address(host);
    }

    static boolean isLocalAddress(String host) {
        try {
            InetAddress addr = ConnectionHelper.from6to4(InetAddress.getByName(host));
            return (addr.isLoopbackAddress() ||
                    addr.isSiteLocalAddress() ||
                    addr.isLinkLocalAddress());
        } catch (UnknownHostException ex) {
            Log.e(ex);
            return false;
        }
    }

    static List<String> getCommonNames(Context context, String domain, int port, int timeout) throws IOException {
        List<String> result = new ArrayList<>();
        InetSocketAddress address = new InetSocketAddress(domain, port);
        SocketFactory factory = SSLSocketFactory.getDefault();
        try (SSLSocket sslSocket = (SSLSocket) factory.createSocket()) {
            EntityLog.log(context, "Connecting to " + address);
            sslSocket.connect(address, timeout);
            EntityLog.log(context, "Connected " + address);

            sslSocket.setSoTimeout(timeout);
            sslSocket.startHandshake();

            Certificate[] certs = sslSocket.getSession().getPeerCertificates();
            for (Certificate cert : certs)
                if (cert instanceof X509Certificate) {
                    try {
                        result.addAll(EntityCertificate.getDnsNames((X509Certificate) cert));
                    } catch (CertificateParsingException ex) {
                        Log.w(ex);
                    }
                }
        }
        return result;
    }
}
