//
//  CartrackPlugin.swift
//  CartrackPlugin
//
//  Created by Andre Grillo on 06/10/2022.
//
import CoreBluetooth
import CartrackBleLockSDK
import Foundation

@objc(CartrackPlugin)
class CartrackPlugin: CDVPlugin {
    var bleTerminal: BleTerminal?
    var callbackCommandDict:[CallbackTypes:CDVInvokedUrlCommand] = [:]
    var sendActionsCommandDict:[bleActionTypes:CDVInvokedUrlCommand] = [:]
    
    //MARK: Plugin Methods
    
    @objc (configure:)
    func configure(_ command: CDVInvokedUrlCommand) {
        if let terminalId = command.arguments[0] as? String {
            bleTerminal = BleService.getTerminal(terminalID: terminalId)
            bleTerminal?.delegate = self
            UserDefaults.standard.set(terminalId, forKey: "terminal_id")
            print("Set Terminal ID successful")
            sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "Set Terminal ID successful")
        }
    }
    
    @objc (saveAuthKey:)
    func saveAuthKey(_ command: CDVInvokedUrlCommand) {
        callbackCommandDict = [.saveAuthKey:command]
        if let authKey = command.arguments[0] as? String {
            bleTerminal?.saveAuthKey(authKey: authKey)
        }
    }
    
    @objc (getAuthKey:)
    func getAuthKey(_ command: CDVInvokedUrlCommand) {
        if let authKey = bleTerminal?.authKey {
            print("AuthKey fetched: \(authKey)")
            sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: authKey)
        }
    }
    
    @objc (scanAndConnectToPeripheral:)
    func scanAndConnectToPeripheral(_ command: CDVInvokedUrlCommand) {
        callbackCommandDict = [.scanAndConnect: command]
            print("Connecting...")
            bleTerminal?.connect()
    }
    
    @objc (disconnect:)
    func disconnect(_ command: CDVInvokedUrlCommand) {
        callbackCommandDict = [.disconnect:command]
        print("Disconnecting...")
        bleTerminal?.disconnect()
    }
    
    @objc (removeAuthKey:)
    func removeAuthKey(_ command: CDVInvokedUrlCommand) {
        bleTerminal?.removeAuthKey()
        if bleTerminal?.hasKey ?? false {
            print("Remove Key failed")
            sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Remove Key failed")
        } else {
            print("Remove Key Successful")
            sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "Remove Key Successful")
        }
    }
    
    @objc (sendAction:)
    func sendAction(_ command: CDVInvokedUrlCommand) {
        if let bleAction = bleActionTypes(rawValue: command.arguments[0] as! String) {
            switch bleAction {
            case .lock:
                sendActionsCommandDict = [.lock:command]
                bleTerminal?.sendAction(.lock)
            case .unlock:
                sendActionsCommandDict = [.unlock:command]
                bleTerminal?.sendAction(.unlock)
            case .horn:
                sendActionsCommandDict = [.horn:command]
                bleTerminal?.sendAction(.horn)
            case .lockState:
                sendActionsCommandDict = [.lockState:command]
                bleTerminal?.sendAction(.lockState)
            case .headlight:
                sendActionsCommandDict = [.headlight:command]
                bleTerminal?.sendAction(.headlight)
            case .unlockNoKeyFob:
                sendActionsCommandDict = [.unlockNoKeyFob:command]
                bleTerminal?.sendAction(.unlockNoKeyFob)
//            case .getConfig:      //ANDROID ONLY
//                sendActionsCommandDict = [.getConfig:command]
//                bleTerminal?.sendAction(.getConfig)
            case .ignitionState:
                sendActionsCommandDict = [.ignitionState:command]
                bleTerminal?.sendAction(.ignitionState)
            case .getStatus:
                sendActionsCommandDict = [.ignitionState:command]
                bleTerminal?.getVehicleStats()
            default:
                print("Action not available on iOS")
                sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Action not available on iOS")
            }
        }
    }
    
    @objc (initErrorHandler:)
    func initErrorHandler(_ command: CDVInvokedUrlCommand) {
        callbackCommandDict = [.onError:command]
    }
    
    @objc (requestPermissions:)
    func requestPermissions(_ command: CDVInvokedUrlCommand) {
        //Only used on Android
        print("Permission request")
    }
    
    @objc (getLockState:)
    func getLockState(_ command: CDVInvokedUrlCommand) {
        let lockState: LockState = bleTerminal?.lockState ?? .unknown
        switch lockState {
        case .locked:
            sendPluginResult(cdvCommand:command, status: CDVCommandStatus_OK, message: "locked")
        case .unlocked:
            sendPluginResult(cdvCommand:command, status: CDVCommandStatus_OK, message: "unlocked")
        default:
            sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "unknown")
        }
    }
    
    func sendPluginResult(cdvCommand: CDVInvokedUrlCommand, status: CDVCommandStatus, message: String, defaultErrorCallback: Bool = false) {
        var pluginResult = CDVPluginResult()
        pluginResult?.setKeepCallbackAs(true)
        pluginResult = CDVPluginResult(status: status, messageAs: message)
        self.commandDelegate!.send(pluginResult, callbackId: cdvCommand.callbackId)
    }
}

extension CartrackPlugin: BleTerminalDelegate {
    
    //MARK: CarTrack SDK Delegate Methods
    
    func bleTerminalDidSavedKey(terminal: BleTerminal, error: BleError?) {
        if let command = callbackCommandDict[.saveAuthKey]{
            callbackCommandDict.removeValue(forKey: .saveAuthKey)
            if let error = error {
                print("Save Key Failed, message: \(error.errorCode) \(error.localizedDescription)")
                sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Save Key Failed, message: \(error.errorCode) \(error.localizedDescription)")
            } else {
                print("Save Key Successful")
                sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "OK")
            }
        }
    }
    
    func bleTerminalDidConnect(terminal: BleTerminal, error: BleError?) {
        if let command = callbackCommandDict[.scanAndConnect]{
            callbackCommandDict.removeValue(forKey: .scanAndConnect)
            if let error = error {
                print("Failed to connect, Error Code: \(error.errorCode) Message: \(error.localizedDescription)")
                sendPluginResult(cdvCommand: command,status: CDVCommandStatus_ERROR, message: "Failed to connect, Error Code: \(error.errorCode) Message: \(error.localizedDescription)")
            } else {
                sendPluginResult(cdvCommand: command,status: CDVCommandStatus_OK, message: "Connected")
            }
        }
    }
    
    func bleTerminalDidAction(terminal: BleTerminal, action: BleAction, error: BleError?) {
        switch action {
        case .lockState:
            print("Action - Vehicle's door is \(terminal.lockState)")
            if let command = sendActionsCommandDict[.lockState]{
                if let error = error {
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Action Failed, message: [\(error.actionCode)] [\(action)] Failed. Reason: [\(error.errorCode)]\(error.localizedDescription)")
                } else {
                    sendActionsCommandDict.removeValue(forKey: .lockState)
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "\(terminal.lockState)")
                }
            }
        case .lock:
            print("Action - Lock Action Success\nLock state: [\(terminal.lockState)]")
            if let command = sendActionsCommandDict[.lock]{
                if let error = error {
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Action Failed, message: [\(error.actionCode)] [\(action)] Failed. Reason: [\(error.errorCode)]\(error.localizedDescription)")
                } else {
                    sendActionsCommandDict.removeValue(forKey: .lock)
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "\(terminal.lockState)")
                }
            }
        case .unlock:
            print("Action - Unlock Action Success\nLock state: [\(terminal.lockState)]")
            if let command = sendActionsCommandDict[.unlock]{
                if let error = error {
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Action Failed, message: [\(error.actionCode)] [\(action)] Failed. Reason: [\(error.errorCode)]\(error.localizedDescription)")
                } else {
                    sendActionsCommandDict.removeValue(forKey: .unlock)
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "\(terminal.lockState)")
                }
            }
        case .unlockNoKeyFob:
            print("Action - Unlock with No Key Fob Action Success\nLock state: [\(terminal.lockState)]")
            if let command = sendActionsCommandDict[.unlockNoKeyFob]{
                if let error = error {
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Action Failed, message: [\(error.actionCode)] [\(action)] Failed. Reason: [\(error.errorCode)]\(error.localizedDescription)")
                } else {
                    sendActionsCommandDict.removeValue(forKey: .unlockNoKeyFob)
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "\(terminal.lockState)")
                }
            }
        case .horn:
            print("Action - Horn Action Success")
            if let command = sendActionsCommandDict[.horn]{
                if let error = error {
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Action Failed, message: [\(error.actionCode)] [\(action)] Failed. Reason: [\(error.errorCode)]\(error.localizedDescription)")
                } else {
                    sendActionsCommandDict.removeValue(forKey: .horn)
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "horn ok")
                }
            }
        case .headlight:
            print("Action - Headlight Action Success")
            if let command = sendActionsCommandDict[.headlight]{
                if let error = error {
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Action Failed, message: [\(error.actionCode)] [\(action)] Failed. Reason: [\(error.errorCode)]\(error.localizedDescription)")
                } else {
                    sendActionsCommandDict.removeValue(forKey: .headlight)
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "headlight ok")
                }
            }
        case .ignitionState:
            print("Action - Vehicle's ignition state is \(terminal.ignitionState)")
            if let command = sendActionsCommandDict[.ignitionState]{
                if let error = error {
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Action Failed, message: [\(error.actionCode)] [\(action)] Failed. Reason: [\(error.errorCode)]\(error.localizedDescription)")
                } else {
                    sendActionsCommandDict.removeValue(forKey: .ignitionState)
                    sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "\(terminal.ignitionState)")
                }
            }
        @unknown default:
            if let command = callbackCommandDict[.onError]{
                sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Unknow action", defaultErrorCallback: true)
            }
            break
        }
        
    }
    
    func bleTerminalDidGetVehicleStats(terminal: BleTerminal, vehicleStats: VehicleStats?, error: BleError?) {
        if let command = sendActionsCommandDict[.ignitionState]{
            if let error = error {
                print("Action Failed, [\(error.actionCode)] get vehicle stats Failed\nReason: [\(error.errorCode)] \(error.localizedDescription)")
                sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Action Failed, [\(error.actionCode)] get vehicle stats Failed\nReason: [\(error.errorCode)] \(error.localizedDescription)", defaultErrorCallback: true)
            } else {
                if let vehicleStatsDict = vehicleStats?.asDictionary() {
                    if let jsonData = try? JSONSerialization.data(withJSONObject: vehicleStatsDict as Any, options: .prettyPrinted) {
                        if let jsonString = String(data: jsonData, encoding: .utf8) {
                            print(jsonString)
                            sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: jsonString)
                        } else {
                            sendPluginResult(cdvCommand: command,status: CDVCommandStatus_ERROR, message: "Error: Get vehicle stats Failed. Reason: Failed to create JSON String from vehicleStats Object")
                        }
                    } else {
                        sendPluginResult(cdvCommand: command,status: CDVCommandStatus_ERROR, message: "Error: Get vehicle stats Failed. Reason: Error serializing JSON from vehicleStatsDict Object")
                    }
                } else {
                    sendPluginResult(cdvCommand: command,status: CDVCommandStatus_ERROR, message: "Error: Get vehicle stats Failed. Reason: Error creating vehicleStatsDict from vehicleStats Object")
                }
            }
        }
    }
    
    func bleTerminalDisconnected(terminal: BleTerminal) {
        print("Disconnected")
        if let command = callbackCommandDict[.disconnect] {
            callbackCommandDict.removeValue(forKey: .disconnect)
            sendPluginResult(cdvCommand: command, status: CDVCommandStatus_OK, message: "Disconnected", defaultErrorCallback: true)
        } else if let command = callbackCommandDict[.onError]{
            sendPluginResult(cdvCommand: command, status: CDVCommandStatus_ERROR, message: "Disconnected", defaultErrorCallback: true)
        }
    }
    
    func bleTerminalSignalUpdate(rssi: Int, strength: BleSignalStrength) {
        print("RSSI: \(rssi) dBm (\(strength))")
    }
}

//MARK: VehicleStats Properties to Dictionary (JSON) extension

extension VehicleStats {
    func asDictionary() -> [String: Any] {
            return ["odometer": odometer as Double,
                    "engineHours": engineHours as Int,
                    "engineRPM": engineRPM as Int,
                    "fuelLevel": fuelLevel as Int,
                    "hazardsIsOn": hazardsIsOn as Bool,
                    "indicators": self.indicators.rawValue,
                    "brakesIsActive": brakesIsActive as Bool,
                    "handBrakeIsActive": handBrakeIsActive as Bool,
                    "lightsIsOn": lightsIsOn as Bool,
                    "driverDoorIsOpen": driverDoorIsOpen as Bool,
                    "passengerDoorIsOpen": passengerDoorIsOpen as Bool,
                    "driverSeatbeltIsEngage": driverSeatbeltIsEngage as Bool,
                    "passengerSeatbeltIsEngage": passengerSeatbeltIsEngage as Bool,
                    "hornIsActive": hornIsActive as Bool
            ]
        }
}

//MARK: Enumerations

enum CallbackTypes: String {
    case saveAuthKey = "SAVE_AUTH_KEY"
    case scanAndConnect = "SCAN_AND_CONNECT_TO_PERIPHERAL"
    case disconnect = "DISCONNECT"
    //case removeAuthKey = "REMOVE_AUTH_KEY"
    case sendAction = "SEND_ACTION"
    case onError = "ON_ERROR"
    //case requestPermissions = "REQUEST_PERMISSIONS"
}

enum bleActionTypes: String {
    case lockState = "GET_LOCK_STATE"
    case lock = "LOCK"
    case unlock = "UNLOCK"
    case unlockNoKeyFob = "UNLOCK_NOKEYFOB"
    case horn = "HORN"
    case headlight = "HEADLIGHT"
    case ignitionState = "IGNITION_STATE"
    //ANDROID ONLY BELOW
    case getConfig = "VEHICLE_GET_CONFIG"
    case getStatus = "VEHICLE_GET_STATUS"
}
