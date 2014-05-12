package com.example.vcfimporter;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.OperationApplicationException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.*;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

/**
 *
 */
public class MyActivity extends Activity {
    public static final String VCF_CONTACT_LOCATION = "/vcf/";
    public static final String TAG = "MyTAG";
    private ProgressBar progressBar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Button buttn = (Button)findViewById(R.id.button);
        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        buttn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progressBar.setVisibility(View.VISIBLE);
                new VcfImporter().execute();
            }
        });

    }

    private class VcfImporter extends AsyncTask<Void,Void,Void>
    {

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Toast.makeText(MyActivity.this,"All imported",Toast.LENGTH_LONG).show();
            progressBar.setVisibility(View.GONE);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Account account = AccountManager.get(MyActivity.this).getAccounts()[0];
            String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
            String path = dir + VCF_CONTACT_LOCATION;
            File f = new File(path);
            if( !f.exists() || !f.isDirectory() )
            {
                Log.d("VCF_IMPORTER", "VCF Dir not found");
                return null;
            }

            File[] files = f.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    if (s.endsWith("vcf"))
                        return true;
                    return false;
                }
            });
            for (File file : files)
            {
                Log.v(TAG,file.getAbsolutePath());
                VCard vcard = null;
                try {
                    vcard = Ezvcard.parse(file).first();
                } catch (IOException e) {
                    Log.v(TAG,e.getMessage());
                }


                ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
                ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                        .build());
                //formatted names
                for (FormattedName formattedName : vcard.getFormattedNames()) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, formattedName.getValue())
                            .build());
                }
                for (StructuredName structuredName : vcard.getStructuredNames()) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, structuredName.getGiven())
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, structuredName.getFamily())
                            .build());
                }

                for (Address address : vcard.getAddresses()) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, address.getCountry())
                                    //.withValue("GEO", address.getGeo())
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.CITY, address.getLocality())
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.POBOX, address.getPoBox())
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, address.getPostalCode())
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.REGION, address.getRegion())
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.STREET, address.getStreetAddress())
                                    //.withValue("EXTENDED_ADDRESS", address.getExtendedAddress())
                            .build());
                }
                for (Photo photo : vcard.getPhotos()) {
                    if(photo.getData() == null) break;
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photo.getData())
                            .build());
                    break;//onlu one pic
                }
                ;
                //birthday
                for (Email email : vcard.getEmails()) {

                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Email.DATA, email.getValue())
                            .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
                            .build());
                }

//            for (Impp impp : vcard.getImpps()) {
//                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
//                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
//                        .withValue(ContactsContract.Data.MIMETYPE,
//                                ContactsContract.CommonDataKinds.Identity.CONTENT_ITEM_TYPE)
//                        .withValue(ContactsContract.CommonDataKinds.Email.DATA, impp.getValue())
//                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
//                        .build());
//            }
                for (Telephone telephone : vcard.getTelephoneNumbers()) {
                    Set<TelephoneType> types = telephone.getTypes();
                    int droidTelType = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
                    if(types.size() > 0)
                    {
                        TelephoneType type = types.iterator().next();
                        //arghs.. no strings in switch ANDROID .. :(
                        if(type.equals(TelephoneType.CELL))
                        {
                            droidTelType = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
                        }
                        else if(type.equals(TelephoneType.FAX))
                        {
                            droidTelType = ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK;
                        }
                        else if(type.equals(TelephoneType.HOME))
                        {
                            droidTelType = ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
                        } else if(type.equals(TelephoneType.WORK))
                        {
                            droidTelType = ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
                        }
                    }
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, telephone.getText())
                            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, droidTelType)
                            .build());
                }

                try {
                    getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                } catch (RemoteException e) {
                    Log.v(TAG, e.getMessage());
                } catch (OperationApplicationException e) {
                    Log.v(TAG, e.getMessage());
                }
            }
            return null;
        }
    }



}
