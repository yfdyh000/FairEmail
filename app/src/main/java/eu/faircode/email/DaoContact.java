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

import android.database.Cursor;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface DaoContact {
    @Query("SELECT * FROM contact" +
            " WHERE account = :account")
    List<EntityContact> getContacts(long account);

    @Query("SELECT contact.*, account.name AS accountName" +
            " FROM contact" +
            " JOIN account ON account.id = contact.account" +
            " ORDER BY times_contacted DESC, last_contacted DESC")
    LiveData<List<TupleContactEx>> liveContacts();

    @Query("SELECT email, name, avatar FROM contact" +
            " WHERE state <> " + EntityContact.STATE_IGNORE +
            " AND (type = " + EntityContact.TYPE_TO +
            " OR type = " + EntityContact.TYPE_FROM + ")" +
            " ORDER BY" +
            " CASE WHEN state = " + EntityContact.STATE_FAVORITE + " THEN 0 ELSE 1 END" +
            ", CASE WHEN avatar IS NULL THEN 1 ELSE 0 END" +
            ", times_contacted DESC" +
            ", last_contacted DESC")
    Cursor getFrequentlyContacted();

    @Query("SELECT *" +
            " FROM contact" +
            " WHERE account = :account" +
            " AND type = :type" +
            " AND email = :email COLLATE NOCASE")
    EntityContact getContact(long account, int type, String email);

    @Query("SELECT *" +
            " FROM contact" +
            " WHERE (:account IS NULL OR account = :account)" +
            " AND (:type IS NULL OR type = :type)" +
            " AND (email LIKE :query COLLATE NOCASE OR name LIKE :query COLLATE NOCASE)" +
            " AND state <> " + EntityContact.STATE_IGNORE +
            " GROUP BY name, email")
    List<EntityContact> searchContacts(Long account, Integer type, String query);

    @Insert
    long insertContact(EntityContact contact);

    @Update
    int updateContact(EntityContact contact);

    @Query("DELETE FROM contact WHERE id = :id")
    int deleteContact(long id);

    @Query("DELETE FROM contact" +
            " WHERE account = :account" +
            " AND type = :type")
    int deleteContact(long account, int type);

    @Query("DELETE FROM contact" +
            " WHERE account = :account" +
            " AND type = :type" +
            " AND email = :email")
    int deleteContact(long account, int type, String email);

    @Query("UPDATE contact SET name = :name WHERE id = :id AND NOT (name IS :name)")
    int setContactName(long id, String name);

    @Query("UPDATE contact SET state = :state WHERE id = :id AND NOT (state IS :state)")
    int setContactState(long id, int state);

    @Query("DELETE FROM contact" +
            " WHERE last_contacted IS NOT NULL" +
            " AND last_contacted < :before" +
            " AND state <> " + EntityContact.STATE_FAVORITE +
            " AND (type = " + EntityContact.TYPE_TO +
            " OR type = " + EntityContact.TYPE_FROM + ")")
    int deleteContacts(long before);

    @Query("DELETE FROM contact" +
            " WHERE (type = " + EntityContact.TYPE_TO +
            " OR type = " + EntityContact.TYPE_FROM + ")")
    int clearContacts();
}
