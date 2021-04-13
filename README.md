# Keyple Android Validation demo app

This is the repository for the 'Eclipse Keyple' Android demo validation application.  
This demo application allows to validate a contract (Season Pass and MultiTrip ticket) written on a card.

It implements simultaneously multiple plugins using the flavors feature for handling mutiple devices :

- [Coppernic](https://github.com/calypsonet/keyple-android-plugin-coppernic)
- [Famoco](https://github.com/calypsonet/keyple-famoco)
- [NFC Reader / OMAPI](https://github.com/calypsonet/keyple-java/tree/develop/android/keyple-plugin)

Here is the link to the github repository containing the source code :

[calypsonet/keyple-android-demo-validation](https://github.com/calypsonet/keyple-android-demo-validation)

## Screens
- Settings (SettingsActivity) : Allows to set the settings of the validation procedure :
  - Location : Where the validation is taking place. The validation location will be written on the newly created event.
  - Battery Powered : Check button to set if the battery powered feature is enable or not.
- Home (HomeActivity) : Displayed only if the 'battered powered' feature is enabled. Allows to launch the card hunting phase
- Card Reader (CardReaderActivity) : Launches the flavor associated Keyple plugin. At this point the user must present the card (PO) that he wishes to validate.
  - Initiaze the Keyple plugin : start detection on NFC and SAM (if available) readers
  - Prepare and defines the default selection requests to be processed when a card is inserted.
  - Listens to detected card tags
  - launches the Validation Procedure when a tag is detected
- Validation result screen (CardSummaryActivity) :
  - If the validation is successfull :
     - Location of the validation
     - Date and hour of the validation
     - Depending on the contract type :
        - Season Pass : End of validity
        - MultiTrip Ticket : number of tickets left
  - If the validation fails :
    - The reason why it failed

## Calypso applications
This demo evolves to provide wide Calypso applet support. For now this demo can support:

* Calypso Prime sample: AID 315449432E49434131h - Structure 05h (CD Light/GTML)
* (Work in progress) Calypso Light sample: AID 315449432E49434133h - Structure 32h (Light Classic)
* Navigo test card: AID  A0000004040125090101h - Structure D7h (Navigo)

## Dependencies

The Android-Keyple library needs multiple dependencies to work.

First we need to import the keyple related dependencies in the `build.gradle` file :

```groovy
    implementation "org.eclipse.keyple:keyple-java-core:1.0.0"
    implementation "org.eclipse.keyple:keyple-java-calypso:1.0.0"
```

Then each devices needs it own dependencies imported. In our case, we use the flavor feature to import only the currently flavor specific device needed dependency.

Here are some examples :

- Coppernic
```groovy
    copernicImplementation "org.eclipse.keyple:keyple-android-plugin-coppernic-ask:1.0.0"
```

- Famoco
```groovy
    famocoImplementation "org.eclipse.keyple:keyple-android-plugin-nfc:1.0.0"
    famocoImplementation "org.eclipse.keyple:keyple-android-plugin-famoco-se-communication:1.0.0"
```

- NFC Reader / OMAPI
```groovy
    omapiImplementation "org.eclipse.keyple:keyple-android-plugin-nfc:1.0.0"
    omapiImplementation "org.eclipse.keyple:keyple-android-plugin-omapi:1.0.0"
```

## Device specific flavors

In Android, a flavor is used to specify custom features. In our case, the specific feature is the device used to run the demo app
and therefore the specific Keyple plugin associated.
This app implements multiple devices plugin at once using this flavor feature.

This feature allows to add a new plugin easily by implementing the following classes :
- ReaderModule : Dagger module class that provides needed components :
  - IReaderRepository : Interface used by the app to communicate with a specific Keyple Android plugin. It implements a set of methods used in the card reader screen to initialize, detect, and communicate with a contactless (card) and contact (SAM) Portable Object.
  - ReaderObservationExceptionHandler : Provides a channel for notifying runtime exceptions that may occur during operations carried out by the monitoring thread(s).
- XXXReaderModule : Class implementing the IReaderModule specific to each device plugin, for example 'CoppernicReaderModule'

In order the make a new flavor work, for example for the Coppernic device, you must declare it in the app's build.gradle file.

Add a product flavor to the `device` flavor dimension
```groovy
    flavorDimensions 'device'
    
    productFlavors {
            coppernic {
                dimension 'device'
                resValue "string", "app_name", "Keyple Coppernic Validation"
                applicationIdSuffix ".copernic"
            }
    }
```

Create the flavors sourceSet folder `copernic` in the `app/src` folder.  
Then create in `copernic` the package folders that will contain the code classes : `org/eclipse/keyple/demo/validator/`

Declare the sourceSet folder associated to the flavor int the buid.gradle file :
```groovy
    sourceSets {
        main.java.srcDirs += 'src/main/kotlin'
        test.java.srcDirs += 'src/test/kotlin'
        
        copernic.java.srcDirs += 'src/copernic/kotlin'
    }
```

Import the associated plugin dependencies using the specific implementation syntax.  
This way it will only be imported if the specific flavors in active.
```groovy
    copernicImplementation "org.eclipse.keyple:keyple-android-plugin-coppernic-ask:1.0.0"
```

## Ticketing implementation

As we have seen previously, the first step in implementing the ticketing layer is the implementation of the IReaderRepository interface specific the currently used device.
Here are the other classes that allows to use
- CardReaderApi
- TicketingSession
- PoObserver

### CardReaderApi

Mainly used to manage the lifecycle of the keyple plugin. this class is used to initialize the plugin and manage the hunt phase.
It is called on the different steps of the card reader activity lifecycle :
- onResume:
  - Initialize the plugin (PO and SAM readers...)
  - Get the ticketing session
  - Start NFC detection
- onPause :
  - Stop NFC detection
- onDestroy :
  - Clear the keyple plugin (remove observers and unregister plugin)

### TicketingSession

The purpose of this class is to communicate with the portable object (=PO, ex: card).

First it prepares the default selection that will be sent to the PO when a tag is detected by setting the AID(s) and the reader protocol(s) of the cards we want to detect and read.

Once a tag is detected, the TicketingSession processes the default selection by retrieving the current CalypsoPo object. This CalypsoPo contains informations about the card (SerialNumber, PoRevision...)

Finally this class is responsible for launching the control procedure and returning its result.

### PoObserver

This class is the reader observer and inherits from Keyple's class :
```groovy
    ObservableReader.ReaderObserver
```
It is class each time a new ReaderEvent (CARD_INSERTED, CARD_MATCHED...) is launched by the Keyple plugin.
This reader is registered when the reader is registered and removed when the reader is unregistered.

## Validation Procedure

For this control demo application, a simple example validation procedure has been implemented. This procedure is implemented in the class 'ValidationProcedure'.

Opening a secure session is mandatory for this procedure since we need to write a new event in the event log. If no SAM is available the procedure fails.

This procedure's main steps :
- Open a Validation (secure) session reading the environment record.
- Event Analysis :
  - Read the environement :
    - If EnvVersionNumber of the Environment structure is not the expected one (==1 for the current version) reject the card.
    - If EnvEndDate points to a date in the past reject the card.
  - Read and unpack the last event record :
    - If EventVersionNumber is not the expected one (==1 for the current version) reject the card.
- Best Contract Search :
  - Create a list of ContractPriority fields that are different from 0 or 31.
  - Order the list by ContractPriority Value.
  - For each element in the list:
    - Read and unpack the contract record for the index being iterated.
    - If ContractVersionNumber is not the expected one (==1 for the current version) reject the card.
    - If ContractValidityEndDate points to a date in the past update the associated ContractPriorty field present in the persistent object to 31 and move to the next element in the list
    - If the ContractTariff value for the contract read is 2 or 3:
      - Read and unpack the counter associated to the contract (1st counter for Contract #1 and so forth).
      - If the counter value is 0 update the associated ContractPriorty field present in the persistent object to 31 and move to the next element in the list
      - If the counter value is > 0 && ContractTariff == 3 && CounterValue < ValidationAmount move to the next element in the list
- Update Counter :
  - Decrement the counter value by the appropriate amount (1 if ContractTariff is 2, and the configured value for the trip if ContractTariff is 3).
- Add new Event :
  - Fill the event structure to update :
    - EventVersionNumber = 1
    - EventDateStamp = Current Date converted to DateCompact
    - EventTimeStamp = Current Time converted to TimeCompact
    - EventLocation = value configured in the validator
    - EventContractUsed = index of the contract found during Best Contract Search
    - ContractPriority1 = Value of index 0 of ContractPriority persistent object
    - ContractPriority2 = Value of index 1 of ContractPriority persistent object
    - ContractPriority3 = Value of index 2 of ContractPriority persistent object
    - ContractPriority4 = Value of index 3 of ContractPriority persistent object
    - EventPadding = 0
- Pack the Event structure and append it to the event file
- END: Close the session

