package com.github.diamondminer88.myq

import io.ktor.util.*
import java.util.*

internal object MyQData {
	val clientSecret = "VUQ0RFhuS3lQV3EyNUJTdw==".decodeBase64String()
	const val clientId = "IOS_CGI_MYQ"
	const val redirectUri = "com.myqops://ios"
	const val tokenScope = "MyQ_Residential offline_access"

	const val authUrl = "https://partner-identity.myq-cloud.com/connect/authorize"
	const val refreshUrl = "https://partner-identity.myq-cloud.com/connect/token"
	const val accountsUrl = "https://accounts.myq-cloud.com/api/v6.0/accounts"

	fun devicesUrl(accountId: UUID) =
		"https://devices.myq-cloud.com/api/v5.2/Accounts/${accountId}/Devices"

	fun garageDoorUrl(accountId: UUID, deviceSerial: String, command: String) =
		"https://account-devices-gdo.myq-cloud.com/api/v5.2/Accounts/$accountId/door_openers/$deviceSerial/$command"

	fun lampUrl(accountId: UUID, deviceSerial: String, command: String) =
		"https://account-devices-lamp.myq-cloud.com/api/v5.2/Accounts/$accountId/lamps/$deviceSerial/$command"
}
