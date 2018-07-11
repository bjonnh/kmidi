package net.bjonnh.audio.kmidi

import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiSystem
import javax.sound.midi.MidiUnavailableException
import javax.sound.midi.ShortMessage
import javax.sound.midi.SysexMessage

open class Control(val physical: Int, val channel: Int, val cc:Int, val name: String)

class Pot(physical: Int, channel: Int, cc: Int, name: String): Control(physical, channel, cc, name)
class ButtonToggleOn(physical: Int, channel: Int, cc:Int, name: String): Control(physical, channel, cc, name)
class ButtonDown(physical: Int, channel: Int, cc:Int, name: String): Control(physical, channel, cc, name)

open class Controller(val device: MidiDevice) {
    open val controls: MutableList<Control> = mutableListOf()
    var blocksWithNoPause = 0

    fun sendBCLText(data: String, msb: Byte, lsb: Byte) {
        //println("Sending $data at $msb:$lsb")
        val deviceNumber: Byte = 0x7f
        val deviceType: Byte = 0x15
        val command: Byte = 0x20
        val message = byteArrayOf(0xf0.toByte(), 0x00, 0x20, 0x32, deviceNumber, deviceType, command,
                msb, lsb)+ data.toByteArray() + byteArrayOf(0xf7.toByte())

        val sysExMessage = SysexMessage()
        sysExMessage.setMessage(message, message.size)
        device.receiver.send(sysExMessage, -1)
    }

    fun sendBCLBlock(block: List<String>) {
        // Send a BCLBlock,
        if (block.size > 16384) {
            println(" Block is too long, cannot send more than 16384 messages in a single block.")
            return
        }
        block.mapIndexed { index, s -> sendBCLText(s, (index/256).toByte(), (index%256).toByte()) }
        // We have to slow things down a bit as it didn't like the messages to be pushed too fast
        if (blocksWithNoPause >= 16) {
            Thread.sleep(500)
            blocksWithNoPause = 0
        } else {
            blocksWithNoPause += 1
        }


    }

    fun sendInit() {
        sendBCLBlock(listOf("\$rev R1", "\$preset",
                "  .init",
                "  .egroups 1",
                "  .fkeys off",
                "  .lock on",
                "\$end"))
    }

    fun sendButtonToggleOnConfig(physical: Int, channel: Int, cc: Int) {

        sendBCLBlock(listOf("\$rev R1",
                "\$button $physical",
                "  .easypar CC $channel $cc 0 127 toggleon",  // Channel, CC, Val0, Val1, Mode
                "  .showvalue on",
                "\$end"))
    }

    fun sendButtonDownConfig(physical: Int, channel: Int, cc: Int) {

        sendBCLBlock(listOf("\$rev R1",
                "\$button $physical",
                "  .easypar CC $channel $cc 0 127 down",  // Channel, CC, Val0, Val1, Mode
                "  .showvalue off",
                "\$end"))
    }

    fun sendPotConfig(physical: Int, channel: Int, cc: Int) {

        sendBCLBlock(listOf("\$rev R1",
                "\$encoder $physical",
                "  .easypar NRPN $channel $cc 0 16383 absolute/14",  // Channel, CC, Val0, Val1, Mode
                "  .mode 1dot",
                "  .showvalue on",
                "  .resolution 100 1000 10000 16383",
                "\$end"))
    }


    fun sendConfig() {
        for (control in controls) {
            if (control is ButtonToggleOn) {
                sendButtonToggleOnConfig(control.physical, control.channel, control.cc)
            } else if (control is ButtonDown) {
                sendButtonDownConfig(control.physical, control.channel, control.cc)
            } else if (control is Pot) {
                sendPotConfig(control.physical, control.channel, control.cc)
            }
    }
}
}

class BCR2000(device: MidiDevice): Controller(device) {
    override val controls = mutableListOf<Control>()
    init {
        // Push pots (physical 1+) from 1 to 8
        controls.addAll((0..7).map { it -> ButtonToggleOn(1+it, 1, 1+it, "KPU_${it+1}") })
        // Upper row (physical ids 33+) from 11 to 18
        controls.addAll((0..7).map { it -> ButtonToggleOn(33+it, 1, 11+it, "KU_${it+1}") })

        // Lower row (physical ids 41+) from 21 to 28
        controls.addAll((0..7).map { it -> ButtonToggleOn(41+it, 1, 21+it, "KL_${it+1}") })


        // User keys (bottom right) from 31 to 34
        controls.addAll((0..3).map { it -> ButtonToggleOn(49+it, 1, 31+it, "KLR_${it+1}") })


        // Functions from 41 to 44
        controls.addAll((0..3).map { it -> ButtonToggleOn(53+it, 1, 41+it, "KFU_${it+1}") })

        // Encoder groups from 51 to 54
        controls.addAll((0..3).map { it -> ButtonToggleOn(57+it, 1, 51+it, "KEG_${it+1}") })

        // Presets 61 and 62
        controls.addAll((0..1).map { it -> ButtonDown(63+it, 1, 61+it, "KPR_${it+1}") })


        // Pots (physical 1+) from 71 to 78
        controls.addAll((0..7).map { it -> Pot(1+it, 1, 71+it, "PU_${it+1}") })

        // Pots (physical 33+) from 81 to 88
        controls.addAll((0..7).map { it -> Pot(33+it, 1, 81+it, "P1_${it+1}") })
        // Pots (physical 41+) from 91 to 98
        controls.addAll((0..7).map { it -> Pot(41+it, 1, 91+it, "P2_${it+1}") })
        // Pots (physical 49+) from 101 to 108
        controls.addAll((0..7).map { it -> Pot(49+it, 1, 101+it, "P3_${it+1}") })
    }

}

fun listDevices() {
    // Obtain information about all the installed synthesizers.
    var device: MidiDevice? = null
    val infos = MidiSystem.getMidiDeviceInfo()
    for (info in infos) {
        if (info != null) {
            if (info.name.subSequence(0,7) == "BCR2000") {
                println("Found a BCR2000")
                try {
                    device = MidiSystem.getMidiDevice(info)
                } catch (e: MidiUnavailableException) {
                    println(" Midi Unavailable for device: ${info.name} ")
                    continue
                    // Handle or throw exception...
                }

                // If that device has no receivers, we do not want to use it
                if (device.maxReceivers == 0) {
                    continue
                }

                println(" Info: Name=${info.name} Description=${info.description} Vendor=${info.vendor} Version=${info.version}")
                if (device != null && device.isOpen) {
                    device.close()
                }
                if (device != null && !device.isOpen) {
                    try {
                        device.open()
                    } catch (e: MidiUnavailableException) {
                        println(" Couldn't open that device")
                        continue
                        // Handle or throw exception...
                    }
                }

                if (device != null && device.isOpen && device.maxReceivers != 0) {
                    val controller = BCR2000(device)
                    controller.sendInit()
                    controller.sendConfig()
                }
                device?.close()
            }
        }
    }
}

fun main(args: Array<String>) {
    listDevices()
}