import com.github.diamondminer88.myq.MyQ
import com.github.diamondminer88.myq.model.MyQDevice
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class MyQTests {
	private val myQ = MyQ()
	lateinit var devices: List<MyQDevice>

	@Test
	@Order(1)
	fun `login with email + password`() {
		assertDoesNotThrow {
			runBlocking {
				myQ.login(System.getenv("email"), System.getenv("password"))
			}
		}
	}

	@Test
	@Order(2)
	fun `login with refresh token and refresh access token`() {
		assertDoesNotThrow {
			runBlocking {
				myQ.refreshToken()
			}
		}
	}

	@Test
	@Order(3)
	fun `refresh accounts`() {
		assertDoesNotThrow {
			runBlocking {
				myQ.refreshAccounts()
			}
		}
	}

	@Test
	@Order(4)
	fun `fetch devices`() {
		assertDoesNotThrow {
			runBlocking {
				devices = myQ.fetchDevices()
			}
		}
	}

	@Test
	fun `open garage door`() {
		assertDoesNotThrow {
			runBlocking {
				if (System.getenv("garagedoor").toBoolean()) {
					val device = devices.first { it.deviceFamily == "garagedoor" }
					myQ.setGarageDoorState(device, open = true)
				}
			}
		}
	}
}
