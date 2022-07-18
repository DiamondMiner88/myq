package com.github.diamondminer88.myq.model

import com.github.diamondminer88.myq.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.*

@Serializable
internal data class MyQDevicesResponse(
	val count: Int,

	@SerialName("items")
	val devices: List<MyQDevice>,
)

@Serializable
public data class MyQDevice(
	@SerialName("serial_number")
	val serial: String,

	@SerialName("device_family")
	val deviceFamily: String,

	@SerialName("device_platform")
	val devicePlatform: String,

	@SerialName("device_type")
	val deviceType: String,

	@SerialName("device_model")
	val deviceModel: String,

	val name: String,

	@SerialName("parent_device_id")
	val parentDeviceSerial: String? = null,

	@SerialName("created_date")
	val createdDate: String,

	@Serializable(with = UUIDSerializer::class)
	@SerialName("account_id")
	val account: UUID,

	val state: JsonObject,
)
