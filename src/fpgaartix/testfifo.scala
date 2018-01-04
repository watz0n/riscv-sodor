package artix

import chisel3._
import chisel3.iotesters._
import freechips.rocketchip.config._

class TopFIFO()(implicit p: Parameters) extends SteppedHWIOTester {
	enable_scala_debug = false
  	enable_printf_debug = false
  	val device_under_test = Module(new FIFOtoDMI())

}