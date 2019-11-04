package flutter.plugins.contactsservice.contactsservice;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.ContactsContract;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;

import io.flutter.plugin.common.MethodChannel;

import static flutter.plugins.contactsservice.contactsservice.ContactsServicePlugin.loadContactPhotoHighRes;

@TargetApi(Build.VERSION_CODES.CUPCAKE)
public  class GetContactsTask extends AsyncTask<Object, Void, ArrayList<HashMap>> {

     protected static final String[] PROJECTION =
            {
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Profile.DISPLAY_NAME,
                    ContactsContract.Contacts.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.PREFIX,
                    ContactsContract.CommonDataKinds.StructuredName.SUFFIX,
                    ContactsContract.CommonDataKinds.Note.NOTE,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                    ContactsContract.CommonDataKinds.Email.DATA,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.TYPE,
                    ContactsContract.CommonDataKinds.Email.LABEL,
                    ContactsContract.CommonDataKinds.Organization.COMPANY,
                    ContactsContract.CommonDataKinds.Organization.TITLE,
                    ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                    ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                    ContactsContract.CommonDataKinds.StructuredPostal.LABEL,
                    ContactsContract.CommonDataKinds.StructuredPostal.STREET,
                    ContactsContract.CommonDataKinds.StructuredPostal.POBOX,
                    ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD,
                    ContactsContract.CommonDataKinds.StructuredPostal.CITY,
                    ContactsContract.CommonDataKinds.StructuredPostal.REGION,
                    ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
                    ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,
            };



    protected final ContentResolver contentResolver;
    private MethodChannel.Result getContactResult;
    private boolean withThumbnails;
    private boolean photoHighResolution;
    private boolean orderByGivenName;

    public GetContactsTask(ContentResolver contentResolver,MethodChannel.Result result, boolean withThumbnails, boolean photoHighResolution, boolean orderByGivenName){
        this.contentResolver = contentResolver;
        this.getContactResult = result;
        this.withThumbnails = withThumbnails;
        this.photoHighResolution = photoHighResolution;
        this.orderByGivenName = orderByGivenName;
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    protected ArrayList<HashMap> doInBackground(Object... params) {
        ArrayList<Contact> contacts;

//            contacts = getContactsFrom(getCursorForPhone(((String) params[0])));

        contacts = getContactsFrom(getCursor(((String) params[0])));

        if (withThumbnails) {
            for(Contact c : contacts){
                final byte[] avatar = loadContactPhotoHighRes(
                        c.identifier, photoHighResolution, contentResolver);
                if (avatar != null) {
                    c.avatar = avatar;
                } else {
                    // To stay backwards-compatible, return an empty byte array rather than `null`.
                    c.avatar = new byte[0];
                }
            }
        }

        if (orderByGivenName)
        {
            Comparator<Contact> compareByGivenName = new Comparator<Contact>() {
                @Override
                public int compare(Contact contactA, Contact contactB) {
                    return contactA.compareTo(contactB);
                }
            };
            Collections.sort(contacts,compareByGivenName);
        }

        //Transform the list of contacts to a list of Map
        ArrayList<HashMap> contactMaps = new ArrayList<>();
        for(Contact c : contacts){
            contactMaps.add(c.toMap());
        }

        return contactMaps;
    }

    protected void onPostExecute(ArrayList<HashMap> result) {
        getContactResult.success(result);
    }


    /**
     * Builds the list of contacts from the cursor
     * @param cursor
     * @return the list of contacts
     */
    private ArrayList<Contact> getContactsFrom(Cursor cursor) {
        HashMap<Integer, Contact> map = new LinkedHashMap<>();

        while (cursor != null && cursor.moveToNext()) {
            int columnIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
            Integer contactId = cursor.getInt(columnIndex);

            if (!map.containsKey(contactId)) {
                map.put(contactId, new Contact(contactId));
            }
            Contact contact = map.get(contactId);

            String mimeType = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE));
            contact.displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

            //NAMES
            if (mimeType.equals(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)) {
                contact.givenName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME));
                contact.middleName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME));
                contact.familyName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME));
                contact.prefix = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.PREFIX));
                contact.suffix = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.SUFFIX));
            }
            // NOTE
            else if (mimeType.equals(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)) {
                contact.note = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Note.NOTE));
            }
            //PHONES
            else if (mimeType.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)){
                String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                int type = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE));
                if (!TextUtils.isEmpty(phoneNumber)){
                    contact.phones.add(new Item(Item.getPhoneLabel(type),phoneNumber));
                }
            }
            //MAILS
            else if (mimeType.equals(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)) {
                String email = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS));
                int type = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE));
                if (!TextUtils.isEmpty(email)) {
                    contact.emails.add(new Item(Item.getEmailLabel(type, cursor),email));
                }
            }
            //ORG
            else if (mimeType.equals(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)) {
                contact.company = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY));
                contact.jobTitle = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.TITLE));
            }
            //ADDRESSES
            else if (mimeType.equals(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)) {
                contact.postalAddresses.add(new PostalAddress(cursor));
            }
            // BIRTHDAY
            else if (mimeType.equals(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)) {
                int eventType = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE));
                if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) {
                    contact.birthday = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE));
                }
            }
        }

        if(cursor != null)
            cursor.close();

        return new ArrayList<>(map.values());
    }


    protected Cursor getCursor(String query) {
        String selection = ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=? OR "
                + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=? OR "
                + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=? OR "
                + ContactsContract.Data.MIMETYPE + "=?";
        String[] selectionArgs = new String[] { ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, };
        if(query != null){
            selectionArgs = new String[]{query + "%"};
            selection = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?";
        }

        return contentResolver.query(ContactsContract.Data.CONTENT_URI, PROJECTION, selection, selectionArgs, null);
    }

}