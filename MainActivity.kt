package com.example.contact_app_recycler_view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView  // ← Add this import
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), ContactAdapter.OnContactActionListener {
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var btnLoadContacts: Button
    private lateinit var btnSort: Button
    private lateinit var searchView: SearchView
    private lateinit var recyclerViewContacts: RecyclerView

    private lateinit var contactAdapter: ContactAdapter
    private val originalContactList = mutableListOf<Contact>()  // Master list
    private var currentList = mutableListOf<Contact>()  // Display list
    private var isAscending = true  // Sort order

    private var currentProfileImageUri: String? = null  // For storing selected image URI
    private var tempImageUriForEdit: String? = null  // Temporary storage for edit dialog

    // Permission launchers
    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                loadContactsFromPhone()
            } else {
                Toast.makeText(this, "Contacts permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val imageUri: Uri? = result.data?.data
            currentProfileImageUri = imageUri?.toString()
            Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show()
        }
    }

    // Image picker for edit dialog
    private val editImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val imageUri: Uri? = result.data?.data
            tempImageUriForEdit = imageUri?.toString()
            Toast.makeText(this, "Image updated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize UI components
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        btnSave = findViewById(R.id.btnSave)
        btnLoadContacts = findViewById(R.id.btnLoadContacts)
        btnSort = findViewById(R.id.btnSort)
        searchView = findViewById(R.id.searchView)
        recyclerViewContacts = findViewById(R.id.recyclerViewContacts)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Setup RecyclerView
        contactAdapter = ContactAdapter(currentList, this)
        recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        recyclerViewContacts.adapter = contactAdapter

        // Button Click Listeners
        btnSave.setOnClickListener {
            saveContact()
        }

        btnLoadContacts.setOnClickListener {
            checkPermissionAndLoadContacts()
        }

        btnSort.setOnClickListener {
            toggleSortOrder()
        }

        // Setup search functionality
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterContacts(newText ?: "")
                return true
            }
        })
    }

    private fun saveContact() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (!validateInputs(name, phone, etName, etPhone)) {
            return
        }

        val newContact = Contact(name, phone, currentProfileImageUri)
        showSaveContactDialog(newContact)
    }

    private fun showSaveContactDialog(contact: Contact) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_dialog_save_contact, null)
        val tvNamePreview = dialogView.findViewById<TextView>(R.id.tvNamePreview)
        val tvPhonePreview = dialogView.findViewById<TextView>(R.id.tvPhonePreview)
        val ivProfilePreview = dialogView.findViewById<ImageView>(R.id.ivProfilePreview)
        val btnSelectImage = dialogView.findViewById<Button>(R.id.btnSelectImage)

        tvNamePreview.text = contact.name
        tvPhonePreview.text = contact.phone

        // Load preview if image selected
        if (!contact.profileImageUri.isNullOrEmpty()) {
            try {
                val imageUri = Uri.parse(contact.profileImageUri)
                ivProfilePreview.setImageURI(imageUri)
            } catch (e: Exception) {
                ivProfilePreview.setImageResource(R.drawable.ic_default_profile)
            }
        } else {
            ivProfilePreview.setImageResource(R.drawable.ic_default_profile)
        }

        btnSelectImage.setOnClickListener {
            selectImageForSave()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Save Contact")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                originalContactList.add(contact)
                filterContacts(searchView.query.toString())
                sortContacts(isAscending)
                Toast.makeText(this, "Contact saved successfully", Toast.LENGTH_SHORT).show()

                // Clear inputs
                etName.text.clear()
                etPhone.text.clear()
                currentProfileImageUri = null
                etName.requestFocus()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun selectImageForSave() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun validateInputs(name: String, phone: String, nameInput: EditText, phoneInput: EditText): Boolean {
        var isValid = true

        if (name.isEmpty()) {
            nameInput.error = "Name is required"
            isValid = false
        }

        if (phone.isEmpty()) {
            phoneInput.error = "Phone number is required"
            isValid = false
        } else if (phone.length < 10 || !phone.all { it.isDigit() || it == '+' }) {
            phoneInput.error = "Enter valid phone number"
            isValid = false
        }

        return isValid
    }

    private fun filterContacts(query: String) {
        if (query.isEmpty()) {
            currentList.clear()
            currentList.addAll(originalContactList)
        } else {
            val filtered = originalContactList.filter { contact ->
                contact.name.contains(query, ignoreCase = true) ||
                        contact.phone.contains(query, ignoreCase = true)
            }.toMutableList()
            currentList.clear()
            currentList.addAll(filtered)
        }
        contactAdapter.updateList(currentList)
    }

    private fun toggleSortOrder() {
        isAscending = !isAscending
        sortContacts(isAscending)
        btnSort.text = if (isAscending) "Sort: A-Z" else "Sort: Z-A"
    }

    private fun sortContacts(ascending: Boolean) {
        currentList.sortWith(compareBy<Contact> { it.name.lowercase() }
            .thenBy { it.phone })

        if (!ascending) {
            currentList.reverse()
        }

        contactAdapter.updateList(currentList)
    }

    override fun onItemClick(position: Int) {
        val contact = currentList[position]
        Toast.makeText(this, "Contact: ${contact.name}\nPhone: ${contact.phone}", Toast.LENGTH_SHORT).show()
    }

    override fun onEditClick(position: Int) {
        showEditDialog(position)
    }

    override fun onDeleteClick(position: Int) {
        showDeleteDialog(position)
    }

    private fun showDeleteDialog(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete this contact?")
            .setPositiveButton("Yes") { _, _ ->
                val contactToDelete = currentList[position]
                val indexInOriginal = originalContactList.indexOfFirst {
                    it.name == contactToDelete.name && it.phone == contactToDelete.phone
                }
                if (indexInOriginal != -1) {
                    originalContactList.removeAt(indexInOriginal)
                }
                currentList.removeAt(position)
                filterContacts(searchView.query.toString())
                Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun checkPermissionAndLoadContacts() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadContactsFromPhone()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> {
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("This app needs permission to read your contacts to display them.")
                    .setPositiveButton("Grant") { _, _ ->
                        requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                    .setNegativeButton("Deny", null)
                    .show()
            }
            else -> {
                requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun loadContactsFromPhone() {
        val loadedContacts = mutableListOf<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: ""
                val phone = it.getString(phoneIndex) ?: ""

                if (name.isNotBlank() && phone.isNotBlank()) {
                    loadedContacts.add(Contact(name, phone, null))
                }
            }
        }

        if (loadedContacts.isEmpty()) {
            Toast.makeText(this, "No contacts found on your phone", Toast.LENGTH_SHORT).show()
            return
        }

        originalContactList.clear()
        originalContactList.addAll(loadedContacts)
        filterContacts(searchView.query.toString())
        sortContacts(isAscending)

        Toast.makeText(this, "${loadedContacts.size} contacts loaded", Toast.LENGTH_SHORT).show()
    }

    private fun showEditDialog(position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_dialog_edit_item, null)
        val etEditName = dialogView.findViewById<EditText>(R.id.etEditName)
        val etEditPhone = dialogView.findViewById<EditText>(R.id.etEditPhone)
        val ivEditProfile = dialogView.findViewById<ImageView>(R.id.ivEditProfile)
        val btnSelectImage = dialogView.findViewById<Button>(R.id.btnSelectEditImage)

        val contact = currentList[position]
        etEditName.setText(contact.name)
        etEditPhone.setText(contact.phone)
        tempImageUriForEdit = contact.profileImageUri

        // Load existing profile image
        if (!contact.profileImageUri.isNullOrEmpty()) {
            try {
                val imageUri = Uri.parse(contact.profileImageUri)
                ivEditProfile.setImageURI(imageUri)
            } catch (e: Exception) {
                ivEditProfile.setImageResource(R.drawable.ic_default_profile)
            }
        } else {
            ivEditProfile.setImageResource(R.drawable.ic_default_profile)
        }

        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            editImagePickerLauncher.launch(intent)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Contact")
            .setView(dialogView)
            .setPositiveButton("Update", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val updatedName = etEditName.text.toString().trim()
            val updatedPhone = etEditPhone.text.toString().trim()

            if (validateInputs(updatedName, updatedPhone, etEditName, etEditPhone)) {
                // Update in current list
                contact.name = updatedName
                contact.phone = updatedPhone
                contact.profileImageUri = tempImageUriForEdit

                // Update in original list
                val indexInOriginal = originalContactList.indexOfFirst {
                    it.name == contact.name && it.phone == contact.phone
                }
                if (indexInOriginal != -1) {
                    originalContactList[indexInOriginal] = contact
                }

                filterContacts(searchView.query.toString())
                sortContacts(isAscending)
                Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }
}