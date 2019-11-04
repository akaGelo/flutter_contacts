package flutter.plugins.contactsservice.contactsservice;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract;

import java.util.ArrayList;

import io.flutter.plugin.common.MethodChannel;

class GetContactsTaskFromPhone extends GetContactsTask {
    public GetContactsTaskFromPhone(ContentResolver contentResolver, MethodChannel.Result result, boolean withThumbnails, boolean photoHighResolution, boolean orderByGivenName) {
        super(contentResolver, result, withThumbnails, photoHighResolution, orderByGivenName);
    }

    @Override
    protected Cursor getCursor(String identifier) {
        if (identifier.isEmpty())
            return null;

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(identifier));
        String[] projection = new String[]{BaseColumns._ID};

        ArrayList<String> contactIds = new ArrayList<>();
        Cursor identifierCursor = contentResolver.query(uri, projection, null, null, null);
        while (identifierCursor != null && identifierCursor.moveToNext()){
            contactIds.add(identifierCursor.getString(identifierCursor.getColumnIndex(BaseColumns._ID)));
        }
        if (identifierCursor!= null)
            identifierCursor.close();

        if (!contactIds.isEmpty()) {
            String contactIdsListString = contactIds.toString().replace("[", "(").replace("]", ")");
            String contactSelection = ContactsContract.Data.CONTACT_ID + " IN " + contactIdsListString;
            return contentResolver.query(ContactsContract.Data.CONTENT_URI, PROJECTION, contactSelection, null, null);
        }

        return null;
    }
}
