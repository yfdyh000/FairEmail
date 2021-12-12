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

import android.content.Context;
import android.net.ParseException;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.core.net.MailTo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class IPInfo {
    private static Map<InetAddress, Organization> addressOrganization = new HashMap<>();

    private final static int FETCH_TIMEOUT = 15 * 1000; // milliseconds

    static Pair<InetAddress, Organization> getOrganization(@NonNull Uri uri, Context context) throws IOException, ParseException {
        if ("mailto".equalsIgnoreCase(uri.getScheme())) {
            MailTo email = MailTo.parse(uri.toString());
            String domain = UriHelper.getEmailDomain(email.getTo());
            if (domain == null)
                throw new UnknownHostException();
            //InetAddress address = DnsHelper.lookupMx(context, domain);
            //if (address == null)
            //    throw new UnknownHostException();
            InetAddress address = InetAddress.getByName(domain);
            return new Pair<>(address, getOrganization(address, context));
        } else {
            String host = uri.getHost();
            if (host == null)
                throw new UnknownHostException();
            InetAddress address = InetAddress.getByName(host);
            return new Pair<>(address, getOrganization(address, context));
        }
    }

    private static Organization getOrganization(InetAddress address, Context context) throws IOException {
        synchronized (addressOrganization) {
            if (addressOrganization.containsKey(address))
                return addressOrganization.get(address);
        }

        // https://ipinfo.io/developers
        URL url = new URL("https://ipinfo.io/" + address.getHostAddress() + "/org");
        Log.i("GET " + url);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setReadTimeout(FETCH_TIMEOUT);
        connection.setConnectTimeout(FETCH_TIMEOUT);
        connection.setRequestProperty("User-Agent", WebViewEx.getUserAgent(context));
        connection.connect();

        Organization organization = new Organization();
        try {
            int status = connection.getResponseCode();
            if (status != HttpsURLConnection.HTTP_OK)
                throw new FileNotFoundException("Error " + status + ": " + connection.getResponseMessage());

            String response = Helper.readStream(connection.getInputStream());
            organization.name = response.trim();
            if ("".equals(organization.name) || "undefined".equals(organization.name))
                organization.name = null;
        } finally {
            connection.disconnect();
        }

        synchronized (addressOrganization) {
            addressOrganization.put(address, organization);
        }

        return organization;
    }

    static class Organization {
        String name;
    }
}
