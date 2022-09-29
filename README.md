# Keyple Validation Demo

This is the repository for the Keyple Android Validation Demo application. 

This demo is an open source project provided by the [Calypso Networks Association](https://calypsonet.org) implementing
the [Eclipse Keyple SDK](https://keyple.org) in a typical use case that can serve as a basis for building a ticketing
ecosystem based on contactless cards and/or NFC smartphones.

The source code and APK are available at  [calypsonet/keyple-android-demo-validation/releases](https://github.com/calypsonet/keyple-android-demo-validation/releases)

The code can be easily adapted to other cards, terminals and business logic.

It shows how to check if a card is authorized to enter a controlled area (entering the transport network with
a Season Pass and/or Multi-trip ticket), a validation event is added in the event log to be checked by the
[Keyple Demo Control](https://github.com/calypsonet/keyple-android-demo-control)  application.
The contracts are loaded in the Calypso card with the Android application of the [Keyple Remote Demo package](https://github.com/calypsonet/keyple-java-demo-remote).

The demo application was tested on the following terminals:
- `Famoco FX205` via the open source plugins [Famoco](https://github.com/calypsonet/keyple-famoco) (for SAM access) and [Android NFC](https://keyple.org/components-java/plugins/nfc/) (for card access).
- `Coppernic C-One 2` via the open source plugin [Coppernic](https://github.com/calypsonet/keyple-android-plugin-coppernic).

The following terminals have also been tested but as they require non-open source libraries, they are not active by default (see [Using proprietary plugins](#using-proprietary-plugins))  
- `Bluebird EF501` via the proprietary plugin [Bluebird](https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib).
- `Flowbird Axio 2` via the proprietary plugin [Flowbird](https://github.com/calypsonet/keyple-android-plugin-flowbird).

As all the exchanges made with the card are cryptographically secured by a security module (SAM), it is mandatory to install it in the terminal for the application to work properly.

## Keyple Demos

This demo is part of a set of three demos:
* [Keyple Remote Demo](https://github.com/calypsonet/keyple-java-demo-remote)
* [Keyple Validation Demo](https://github.com/calypsonet/keyple-android-demo-validation)
* [Keyple Control Demo](https://github.com/calypsonet/keyple-android-demo-control)

These demos are all based on a common library that defines elements such as constants and data structures implemented 
for the logic of the ticketing application.: [Keyple Demo Common Library](https://github.com/calypsonet/keyple-demo-common-lib).

Please refer to the [README](https://github.com/calypsonet/keyple-demo-common-lib/blob/main/README.md) 
file of this library to discover these data structures.

## Validation Procedure

### Validation Use Case

This use case searches for the best candidate for validation from the existing contracts in the card. 

The goal is to minimize the number of records read, and thus commands exchanged with the card, while being able to
quickly ascertain if the application has or not any valid contracts for validation.

If a contract record needs to be analysed and is found to be expired, then its priority value must be updated to ensure
that in a next operation (and until the contract is reloaded or replaced) time will not needlessly be wasted in reading
this contract.

If the contract has an associated counter (depending on the ContractTariff field value) its value shall be decreased by
the required amount for the operation and a new event will be added.

Steps:
1. Detection and Selection
2. Event Analysis
3. Best Contract Search
4. Update Counter
5. Add new event

### Process

For this validation demo application, a simple example validation procedure has been implemented. 
This procedure is implemented in the `CardRepository` class.

Opening a Calypso secure session is mandatory for this procedure since we need to write a new event in the event log. 
If no Calypso SAM is present, then the procedure will not execute and display an error.

This procedure's main steps are as follows:
- Detection and Selection
  - Detection Analysis:
    - If AID not found reject the card.
  - Selection Analysis:
    - If File Structure unknown reject the card.
- Environment Analysis:
  - Open a Validation session (Calypso Secure Session) reading the environment record.
  - Unpack environment structure from the binary present in the environment record:
    - If `EnvVersionNumber` of the `Environment` structure is not the expected one (==1 for the current version) reject the card.
    - If `EnvEndDate` points to a date in the past reject the card.
- Event Analysis:
  - Read and unpack the last event record:
    - If `EventVersionNumber` is not the expected one (==1 for the current version) reject the card.
  - Store the `ContractPriority` fields in a persistent object.
- Best Contract Search:
  - Create a list of `ContractPriority` fields that are different from 0 or 31.
  - If the list is empty go to **END**.
  - Order the list by `ContractPriority` Value.
  - For each element in the list:
    - Read and unpack the contract record for the index being iterated.
    - If `ContractVersionNumber` is not the expected one (==1 for the current version) reject the card.
    - If `ContractValidityEndDate` points to a date in the past update the associated `ContractPriorty` field present in the persistent object to 31 and move to the next element in the list
    - If the `ContractTariff` value for the contract read is 2 or 3:
      - Read and unpack the counter associated to the contract (1st counter for Contract #1 and so forth).
      - If the counter value is 0 update the associated `ContractPriorty` field present in the persistent object to 31 and move to the next element in the list
      - If the counter value is > 0 && `ContractTariff` == 3 && `CounterValue` < `ValidationAmount` move to the next element in the list
- Update Counter:
  - Decrement the counter value by the appropriate amount (1 if `ContractTariff` is 2, and the configured value for the trip if `ContractTariff` is 3).
- Add new Event:
  - Fill the event structure to update:
    - `EventVersionNumber` = 1
    - `EventDateStamp` = Current Date converted to `DateCompact`
    - `EventTimeStamp` = Current Time converted to `TimeCompact`
    - `EventLocation` = value configured in the validator
    - `EventContractUsed` = index of the contract found during Best Contract Search
    - `ContractPriority1` = Value of index 0 of `ContractPriority` persistent object
    - `ContractPriority2` = Value of index 1 of `ContractPriority` persistent object
    - `ContractPriority3` = Value of index 2 of `ContractPriority` persistent object
    - `ContractPriority4` = Value of index 3 of `ContractPriority` persistent object
    - `EventPadding` = 0
  - Pack the Event structure and append it to the event file
  - **END**: Close the session

## Screens

- Device selection (`DeviceSelectionActivity`): Allows you to indicate the type of device used, in order to use the associated plugin.
  - Initially, devices using proprietary plugins are grayed out.
- Settings (`SettingsActivity`): Allows to set the settings of the validation procedure:
  - Location: Where the validation is taking place. The validation location will be written on the newly created event.
  - Battery Powered: Check button to set if the battery powered feature is enable or not.
- Home (`HomeActivity`): Displayed only if the 'battered powered' feature is enabled. Allows to launch the card detection phase.
- Reader (`ReaderActivity`): Initializes the Keyple plugin. At this point the user must present the card that he wishes to validate.
  - Initialize the Keyple plugin: start detection on NFC and SAM (if available) readers.
  - Prepare and defines the default selection requests to be processed when a card is inserted.
  - Listens to detected cards.
  - Launches the Validation Procedure when a card is detected.
- Validation result screen (`CardSummaryActivity`):
  - If the validation is successful, display:
     - Location of the validation.
     - Date and hour of the validation.
     - Depending on the contract type:
        - Season Pass: End of validity.
        - MultiTrip Ticket: number of tickets left.
  - If the validation fails, display the reason.

## Ticketing implementation

Below are the classes useful for implementing the ticketing layer:
- `TicketingService`
- `ReaderRepository`
- `ReaderActivity.CardReaderObserver`
- `CardRepository`

### TicketingService

This service is the orchestrator of the ticketing process.

Mainly used to manage the lifecycle of the Keyple plugin.
This service is used to initialize the plugin and manage the card detection phase.
It is called on the different steps of the reader activity lifecycle:
- onResume:
  - Initialize the plugin (Card and SAM readers...)
  - Get the ticketing session
  - Start NFC detection
- onPause:
  - Stop NFC detection
- onDestroy:
  - Clear the Keyple plugin (remove observers and unregister plugin)
  
It prepares and scheduled the selection scenario that will be sent to the card when a card is detected by setting
the AID(s) and the reader protocol(s) of the cards we want to detect and read.

Once a card is detected, the service processes the selection scenario by retrieving the current `CalypsoCard` object.
This object contains information about the card (serial number, card revision...)

Finally, this class is responsible for launching the validation procedure and returning its result.

### ReaderRepository

This service is the interface between the business layer and the reader.

### ReaderActivity.CardReaderObserver

This class is the reader observer and inherits from Keyple class `CardReaderObserverSpi`

It is invoked each time a new `CardReaderEvent` (`CARD_INSERTED`, `CARD_MATCHED`...) is launched by the Keyple plugin.
This reader is registered when the reader is registered and removed when the reader is unregistered.

### CardRepository

This class contains the implementation of the "Validation" procedure.

## Using proprietary plugins

By default, proprietary plugins are deactivated.
If you want to activate them, then here is the procedure to follow:
1. make an explicit request to CNA to obtain the desired plugin,
2. copy the plugin into the `/app/libs/` directory,
3. delete in the `/app/libs/` directory the plugin with the same name but suffixed with `-mock` (e.g. xxx-mock.aar),
4. compile the project via the gradle `build` command,
5. deploy the new apk on the device.

