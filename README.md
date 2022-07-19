# myQ ![Views](https://hits.seeyoufarm.com/api/count/incr/badge.svg?url=https%3A%2F%2Fgithub.com%2FDiamondMiner88%2Fmyq%2F&count_bg=%2379C83D&title_bg=%23555555&icon=github.svg&icon_color=%23E7E7E7&title=views&edge_flat=true) ![JitPack](https://img.shields.io/jitpack/v/github/DiamondMiner88/myq?style=flat-square)

A Kotlin library for using the myQ API to interact with Liftmaster/Chamberlain devices.

## Dependency

```kt
repositories {
	maven("https://jitpack.io")
}

dependencies {
	implementation("com.github.DiamondMiner88:myq:1.0.0")
}
```

## Usage

```kt
MyQ().apply {
	login("email@example.com", "password")
	// OR
	login("[refresh_token]")

	// Save this if using multiple times to shorten login procces
	getRefreshToken()

	// Cache this during runtime only
	val devices = fetchDevices()
	device.forEach {
		println("${it.serial} ${it.deviceFamily}: ${it.name}")
	}

	val goDevice = device.find { it.deviceFamily == "garagedoor" }
	setGarageDoorState(goDevice, open = true)

	// Same thing
	setLampState(lampDevice, isOn = true)
}
```

## Limitations

- Not the entire API is implemented
- Locks are not supported (yet)
- Device states are just parsed into a JsonObject for now, since I do not have the full data for all types of devices.
    - If you have an account with locks/lamps registered on it, I would appreciate it if you would let me test with it! (@email on user page)
