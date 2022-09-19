# Keyple Validation Demo

This is the repository for the Keyple Android Validation Demo application. 

This demo is an open source project provided by [Calypso Networks Association](https://calypsonet.org),
you can adapt the demo for your cards, terminals, projects, etc. 

This demo shows how to easily validate a contract (Season Pass and/or Multi-trip ticket) written on a Calypso card
using the [Eclipse Keyple](https://keyple.org) components.

The demo application runs on the following devices:
- `Bluebird` via the proprietary plugin [Bluebird](https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib).
- `Coppernic` via the open source plugin [Coppernic](https://github.com/calypsonet/keyple-android-plugin-coppernic).
- `Famoco` via the open source plugins [Famoco](https://github.com/calypsonet/keyple-famoco) (for SAM access) and [Android NFC](https://keyple.org/components-java/plugins/nfc/) (for card access).
- `Flowbird` via the proprietary plugin [Flowbird](https://github.com/calypsonet/keyple-android-plugin-flowbird).

The source code and APK are available at  [calypsonet/keyple-android-demo-validation/releases](https://github.com/calypsonet/keyple-android-demo-validation/releases)

By default, proprietary plugins are deactivated.
If you want to activate them, then here is the procedure to follow:
1. make an explicit request to CNA to obtain the desired plugin,
2. copy the plugin into the `/app/libs/` directory,
3. delete in the `/app/libs/` directory the plugin with the same name but suffixed with `-mock` (e.g. xxx-mock.aar),
4. compile the project via the gradle `build` command,
5. deploy the new apk on the device.

## Keyple Demos

This demo is part of a set of three demos:
* [Keyple Remote Demo](https://github.com/calypsonet/keyple-java-demo-remote)
* [Keyple Validation Demo](https://github.com/calypsonet/keyple-android-demo-validation)
* [Keyple Control Demo](https://github.com/calypsonet/keyple-android-demo-control)

## Calypso Card Applications

The demo works with the cards provided in the [Test kit](https://calypsonet.org/technical-support-documentation/)

This demo can be used with Calypso cards with the following configurations:
* AID 315449432E49434131h - File Structure 05h (CD Light/GTML Compatibility)
* AID 315449432E49434131h - File Structure 02h (Revision 2 Minimum with MF files)
* AID 315449432E49434133h - File Structure 32h (Calypso Light Classic)
* AID A0000004040125090101h - File Structure 05h (CD Light/GTML Compatibility)

## Validation Procedure

### Data Structures

#### Environment/Holder structure
            
| Field Name           | Bits | Description                                        |     Type      |  Status   |
|:---------------------|-----:|:---------------------------------------------------|:-------------:|:---------:|
| EnvVersionNumber     |    8 | Data structure version number                      | VersionNumber | Mandatory | 
| EnvApplicationNumber |   32 | Card application number (unique system identifier) |      Int      | Mandatory |
| EnvIssuingDate       |   16 | Card application issuing date                      |  DateCompact  | Mandatory | 
| EnvEndDate           |   16 | Card application expiration date                   |  DateCompact  | Mandatory | 
| HolderCompany        |    8 | Holder company                                     |      Int      | Optional  | 
| HolderIdNumber       |   32 | Holder Identifier within HolderCompany             |      Int      | Optional  | 
| EnvPadding           |  120 | Padding (bits to 0)                                |    Binary     | Optional  | 
            
#### Event structure            

| Field Name         | Bits | Description                                   |     Type      |  Status   |
|:-------------------|-----:|:----------------------------------------------|:-------------:|:---------:|
| EventVersionNumber |    8 | Data structure version number                 | VersionNumber | Mandatory | 
| EventDateStamp     |   16 | Date of the event                             |  DateCompact  | Mandatory | 
| EventTimeStamp     |   16 | Time of the event                             |  TimeCompact  | Mandatory | 
| EventLocation      |   32 | Location identifier                           |      Int      | Mandatory | 
| EventContractUsed  |    8 | Index of the contract used for the validation |      Int      | Mandatory | 
| ContractPriority1  |    8 | Priority for contract #1                      | PriorityCode  | Mandatory | 
| ContractPriority2  |    8 | Priority for contract #2                      | PriorityCode  | Mandatory | 
| ContractPriority3  |    8 | Priority for contract #3                      | PriorityCode  | Mandatory | 
| ContractPriority4  |    8 | Priority for contract #4                      | PriorityCode  | Mandatory | 
| EventPadding       |  120 | Padding (bits to 0)                           |    Binary     | Optional  | 
            
#### Contract structure             

| Field Name              | Bits | Description                          |        Type         |  Status   |
|:------------------------|-----:|:-------------------------------------|:-------------------:|:---------:|
| ContractVersionNumber   |    8 | Data structure version number        |    VersionNumber    | Mandatory | 
| ContractTariff          |    8 | Contract Type                        |    PriorityCode     | Mandatory | 
| ContractSaleDate        |   16 | Sale date of the contract            |     DateCompact     | Mandatory | 
| ContractValidityEndDate |   16 | Last day of validity of the contract |     DateCompact     | Mandatory | 
| ContractSaleSam         |   32 | SAM which loaded the contract        |         Int         | Optional  | 
| ContractSaleCounter     |   24 | SAM auth key counter value           |         Int         | Optional  | 
| ContractAuthKvc         |    8 | SAM auth key KVC                     |         Int         | Optional  | 
| ContractAuthenticator   |   24 | Security authenticator               | Authenticator (Int) | Optional  | 
| ContractPadding         |   96 | Padding (bits to 0)                  |       Binary        | Optional  | 
            
#### Counter structure          

| Field Name   | Bits | Description     | Type |  Status   |
|:-------------|-----:|:----------------|:----:|:---------:|
| CounterValue |   24 | Number of trips | Int  | Mandatory | 

### Data Types

| Name          | Bits | Description                                                                                                                                                        |
|:--------------|-----:|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DateCompact   |   16 | Number of days since January 1st, 2010 (being date 0). Maximum value is 16,383, last complete year being 2053. All dates are in legal local time.                  |
| PriorityCode  |    8 | Types of contracts defined: <br>0 Forbidden (present in clean records only)<br>1 Season Pass<br>2 Multi-trip ticket<br>3 Stored Value<br>4 to 30 RFU<br>31 Expired |
| TimeCompact   |   16 | Time in minutes, value = hour*60+minute (0 to 1,439)                                                                                                               |    
| VersionNumber |    8 | Data model version:<br>0 Forbidden (undefined)<br>1 Current version<br>2..254 RFU<br>255 Forbidden (reserved)                                                      |

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
This procedure is implemented in the `ValidationProcedure` class.

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
- `MainService`
- `ReaderService`
- `ReaderActivity.CardReaderObserver`
- `TicketingService`

### MainService

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

### ReaderService

This service is the interface between the business layer and the reader.

### ReaderActivity.CardReaderObserver

This class is the reader observer and inherits from Keyple class `CardReaderObserverSpi`

It is invoked each time a new `CardReaderEvent` (`CARD_INSERTED`, `CARD_MATCHED`...) is launched by the Keyple plugin.
This reader is registered when the reader is registered and removed when the reader is unregistered.

### TicketingService

This service is the orchestrator of the ticketing process.

First it prepares and scheduled the selection scenario that will be sent to the card when a card is detected by setting
the AID(s) and the reader protocol(s) of the cards we want to detect and read.

Once a card is detected, the service processes the selection scenario by retrieving the current `CalypsoCard` object.
This object contains information about the card (serial number, card revision...)

Finally, this class is responsible for launching the validation procedure and returning its result.

