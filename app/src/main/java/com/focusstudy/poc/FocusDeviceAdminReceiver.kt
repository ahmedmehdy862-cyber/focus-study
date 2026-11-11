package com.focusstudy.poc

import android.app.admin.DeviceAdminReceiver

/**
 * Admin component used only for the optional Device Owner mode.
 *
 * Being a plain device admin is not enough: [DeviceOwnerController] checks for Device Owner before
 * suspending anything. Provisioning is done once from a computer with:
 *   adb shell dpm set-device-owner com.focusstudy.poc/.FocusDeviceAdminReceiver
 */
class FocusDeviceAdminReceiver : DeviceAdminReceiver()
