package flutter.plugins.contactsservice.contactsservice;

import static android.provider.ContactsContract.CommonDataKinds;
import static android.provider.ContactsContract.CommonDataKinds.Email;
import static android.provider.ContactsContract.CommonDataKinds.Organization;
import static android.provider.ContactsContract.CommonDataKinds.Phone;
import static android.provider.ContactsContract.CommonDataKinds.StructuredName;
import static android.provider.ContactsContract.CommonDataKinds.StructuredPostal;

import android.annotation.TargetApi;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@TargetApi(Build.VERSION_CODES.ECLAIR)
public class ContactsServicePlugin implements MethodCallHandler {

  ContactsServicePlugin(ContentResolver contentResolver){
    this.contentResolver = contentResolver;
  }

  private static final String LOG_TAG = "flutter_contacts";
  private final ContentResolver contentResolver;
  private final ExecutorService executor =
      new ThreadPoolExecutor(0, 10, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1000));

  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "github.com/clovisnicolas/flutter_contacts");
    channel.setMethodCallHandler(new ContactsServicePlugin(registrar.context().getContentResolver()));
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    switch(call.method){
      case "getContacts": {
        this.getContacts((String)call.argument("query"), (boolean)call.argument("withThumbnails"), (boolean)call.argument("photoHighResolution"), (boolean)call.argument("orderByGivenName"), result);
        break;
      }case "getContactsForPhone": {
        this.getContactsForPhone((String)call.argument("phone"), (boolean)call.argument("withThumbnails"), (boolean)call.argument("photoHighResolution"), (boolean)call.argument("orderByGivenName"), result);
        break;
      } case "getAvatar": {
        final Contact contact = Contact.fromMap((HashMap)call.argument("contact"));
        this.getAvatar(contact, (boolean)call.argument("photoHighResolution"), result);
        break;
      } case "addContact": {
        final Contact contact = Contact.fromMap((HashMap)call.arguments);
        if (this.addContact(contact)) {
          result.success(null);
        } else {
          result.error(null, "Failed to add the contact", null);
        }
        break;
      } case "deleteContact": {
        final Contact contact = Contact.fromMap((HashMap)call.arguments);
        if (this.deleteContact(contact)) {
          result.success(null);
        } else {
          result.error(null, "Failed to delete the contact, make sure it has a valid identifier", null);
        }
        break;
      } case "updateContact": {
        final Contact contact = Contact.fromMap((HashMap)call.arguments);
        if (this.updateContact(contact)) {
          result.success(null);
        } else {
          result.error(null, "Failed to update the contact, make sure it has a valid identifier", null);
        }
        break;
      } default: {
        result.notImplemented();
        break;
      }
    }
  }



  @TargetApi(Build.VERSION_CODES.ECLAIR)
  private void getContacts(String query, boolean withThumbnails, boolean photoHighResolution, boolean orderByGivenName, Result result) {
    new GetContactsTask(contentResolver, result, withThumbnails, photoHighResolution, orderByGivenName).executeOnExecutor(executor, query);
  }

  private void getContactsForPhone(String phone, boolean withThumbnails, boolean photoHighResolution, boolean orderByGivenName, Result result) {
    new GetContactsTaskFromPhone(contentResolver, result, withThumbnails, photoHighResolution, orderByGivenName).executeOnExecutor(executor, phone);
  }





  private void setAvatarDataForContactIfAvailable(Contact contact) {
    Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.identifier);
    Uri photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
    Cursor avatarCursor = contentResolver.query(photoUri,
            new String[] {ContactsContract.Contacts.Photo.PHOTO}, null, null, null);
    if (avatarCursor != null && avatarCursor.moveToFirst()) {
      byte[] avatar = avatarCursor.getBlob(0);
      contact.avatar = avatar;
    }
    if (avatarCursor != null) {
      avatarCursor.close();
    }
  }

  private void getAvatar(final Contact contact, final boolean highRes,
      final Result result) {
    new GetAvatarsTask(contact, highRes, contentResolver, result).executeOnExecutor(this.executor);
  }

  private static class GetAvatarsTask extends AsyncTask<Void, Void, byte[]> {
    final Contact contact;
    final boolean highRes;
    final ContentResolver contentResolver;
    final Result result;

    GetAvatarsTask(final Contact contact, final boolean highRes,
        final ContentResolver contentResolver, final Result result) {
      this.contact = contact;
      this.highRes = highRes;
      this.contentResolver = contentResolver;
      this.result = result;
    }

    @Override
    protected byte[] doInBackground(final Void... params) {
      // Load avatar for each contact identifier.
      return loadContactPhotoHighRes(contact.identifier, highRes, contentResolver);
    }

    @Override
    protected void onPostExecute(final byte[] avatar) {
      result.success(avatar);
    }
  }

  static byte[] loadContactPhotoHighRes(final Integer identifier,
      final boolean photoHighResolution, final ContentResolver contentResolver) {
    try {
      final Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, identifier);
      final InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri, photoHighResolution);

      if (input == null) return null;

      final Bitmap bitmap = BitmapFactory.decodeStream(input);
      input.close();

      final ByteArrayOutputStream stream = new ByteArrayOutputStream();
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
      final byte[] bytes = stream.toByteArray();
      stream.close();
      return bytes;
    } catch (final IOException ex){
      Log.e(LOG_TAG, ex.getMessage());
      return null;
    }
  }

  private boolean addContact(Contact contact){

    ArrayList<ContentProviderOperation> ops = new ArrayList<>();

    ContentProviderOperation.Builder op = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null);
    ops.add(op.build());

    op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.GIVEN_NAME, contact.givenName)
            .withValue(StructuredName.MIDDLE_NAME, contact.middleName)
            .withValue(StructuredName.FAMILY_NAME, contact.familyName)
            .withValue(StructuredName.PREFIX, contact.prefix)
            .withValue(StructuredName.SUFFIX, contact.suffix);
    ops.add(op.build());

    op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.Note.NOTE, contact.note);
    ops.add(op.build());

    op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
            .withValue(Organization.COMPANY, contact.company)
            .withValue(Organization.TITLE, contact.jobTitle);
    ops.add(op.build());

    //Photo
    op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, contact.avatar)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
    ops.add(op.build());

    op.withYieldAllowed(true);

    //Phones
    for(Item phone : contact.phones){
      op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
              .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
              .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
              .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.value);

      if (Item.stringToPhoneType(phone.label) == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM){
        op.withValue( ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM );
        op.withValue(ContactsContract.CommonDataKinds.Phone.LABEL, phone.label);
      } else
        op.withValue( ContactsContract.CommonDataKinds.Phone.TYPE, Item.stringToPhoneType(phone.label) );

      ops.add(op.build());
    }

    //Emails
    for (Item email : contact.emails) {
      op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
              .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
              .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
              .withValue(CommonDataKinds.Email.ADDRESS, email.value)
              .withValue(CommonDataKinds.Email.TYPE, Item.stringToEmailType(email.label));
      ops.add(op.build());
    }
    //Postal addresses
    for (PostalAddress address : contact.postalAddresses) {
      op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
              .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
              .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
              .withValue(CommonDataKinds.StructuredPostal.TYPE, PostalAddress.stringToPostalAddressType(address.label))
              .withValue(CommonDataKinds.StructuredPostal.LABEL, address.label)
              .withValue(CommonDataKinds.StructuredPostal.STREET, address.street)
              .withValue(CommonDataKinds.StructuredPostal.CITY, address.city)
              .withValue(CommonDataKinds.StructuredPostal.REGION, address.region)
              .withValue(CommonDataKinds.StructuredPostal.POSTCODE, address.postcode)
              .withValue(CommonDataKinds.StructuredPostal.COUNTRY, address.country);
      ops.add(op.build());
    }

    try {
      contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private boolean deleteContact(Contact contact){
    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
            .withSelection(ContactsContract.RawContacts.CONTACT_ID + "=?", new String[]{String.valueOf(contact.identifier)})
            .build());
    try {
      contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
      return true;
    } catch (Exception e) {
      return false;

    }
  }

  private boolean updateContact(Contact contact) {
    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    ContentProviderOperation.Builder op;

    // Drop all details about contact except name
    op = ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                    new String[]{String.valueOf(contact.identifier), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE});
    ops.add(op.build());

    op = ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                    new String[]{String.valueOf(contact.identifier), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE});
    ops.add(op.build());

    op = ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(ContactsContract.Data.CONTACT_ID +"=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                    new String[]{String.valueOf(contact.identifier), ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE});
    ops.add(op.build());

    op = ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(ContactsContract.Data.CONTACT_ID +"=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                    new String[]{String.valueOf(contact.identifier), ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE});
    ops.add(op.build());

    op = ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                    new String[]{String.valueOf(contact.identifier), ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE});
    ops.add(op.build());

    //Photo
    op = ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                    new String[]{String.valueOf(contact.identifier), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE});
    ops.add(op.build());

    // Update data (name)
    op = ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                    new String[]{String.valueOf(contact.identifier), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE})
            .withValue(StructuredName.GIVEN_NAME, contact.givenName)
            .withValue(StructuredName.MIDDLE_NAME, contact.middleName)
            .withValue(StructuredName.FAMILY_NAME, contact.familyName)
            .withValue(StructuredName.PREFIX, contact.prefix)
            .withValue(StructuredName.SUFFIX, contact.suffix);
    ops.add(op.build());

    // Insert data back into contact
    op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
            .withValue(Organization.TYPE, Organization.TYPE_WORK)
            .withValue(Organization.COMPANY, contact.company)
            .withValue(Organization.TITLE, contact.jobTitle);
    ops.add(op.build());

    op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
            .withValue(CommonDataKinds.Note.NOTE, contact.note);
    ops.add(op.build());

    //Photo
    op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
          .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
          .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
          .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, contact.avatar)
          .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
    ops.add(op.build());


    for (Item phone : contact.phones) {
      op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
              .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
              .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
              .withValue(Phone.NUMBER, phone.value);

      if (Item.stringToPhoneType(phone.label) == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM){
        op.withValue( ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM );
        op.withValue(ContactsContract.CommonDataKinds.Phone.LABEL, phone.label);
      } else
        op.withValue( ContactsContract.CommonDataKinds.Phone.TYPE, Item.stringToPhoneType(phone.label) );

      ops.add(op.build());
    }

    for (Item email : contact.emails) {
      op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
              .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
              .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
              .withValue(CommonDataKinds.Email.ADDRESS, email.value)
              .withValue(CommonDataKinds.Email.TYPE, Item.stringToEmailType(email.label));
      ops.add(op.build());
    }

    for (PostalAddress address : contact.postalAddresses) {
      op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
              .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
              .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
              .withValue(CommonDataKinds.StructuredPostal.TYPE, PostalAddress.stringToPostalAddressType(address.label))
              .withValue(CommonDataKinds.StructuredPostal.STREET, address.street)
              .withValue(CommonDataKinds.StructuredPostal.CITY, address.city)
              .withValue(CommonDataKinds.StructuredPostal.REGION, address.region)
              .withValue(CommonDataKinds.StructuredPostal.POSTCODE, address.postcode)
              .withValue(CommonDataKinds.StructuredPostal.COUNTRY, address.country);
      ops.add(op.build());
    }

    try {
      contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

}
