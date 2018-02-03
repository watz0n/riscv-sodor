//=======================================================================
// Unit-Test via DMI interface
// Watson Huang
// Feb. 3, 2018
// 
// RVDMI Unit-Test
// Control core by DMI simulator in Scala/Chisel code
//=======================================================================
package tests

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import org.scalatest._              
import org.scalatest.exceptions._

import Common._
import Sodor._

class RVDMIPeekPokeTester(dut: SodorTile, instlist: List[(UInt, UInt)], target: (UInt, UInt, Int)) extends PeekPokeTester(dut)  {

    //Accept response anytime
    poke(dut.io.dmi.resp.ready, 1)

    //Initialize, delay 1T
    poke(dut.io.dmi.req.bits.op, DMConsts.dmi_OP_NONE)
    poke(dut.io.dmi.req.bits.addr, 0x00)
    poke(dut.io.dmi.req.bits.data, 0x00)
    poke(dut.io.dmi.req.valid, 0)
    step(1)

    for (inst <- instlist) {
        val (iaddr:UInt, idata:UInt) = inst

        poke(dut.io.dmi.req.bits.op, DMConsts.dmi_OP_WRITE)
        poke(dut.io.dmi.req.bits.addr, DMI_RegAddrs.DMI_SBADDRESS0)
        poke(dut.io.dmi.req.bits.data, iaddr)
        poke(dut.io.dmi.req.valid, 1)

        while(peek(dut.io.dmi.resp.valid) == BigInt(0)) { step(1) }
        step(1)


        poke(dut.io.dmi.req.bits.op, DMConsts.dmi_OP_WRITE)
        poke(dut.io.dmi.req.bits.addr, DMI_RegAddrs.DMI_SBDATA0)
        poke(dut.io.dmi.req.bits.data, idata)
        poke(dut.io.dmi.req.valid, 1)
        
        while(peek(dut.io.dmi.resp.valid) == BigInt(0)) { step(1) }
        step(1)
    }

    poke(dut.io.dmi.req.bits.op, DMConsts.dmi_OP_WRITE)
    poke(dut.io.dmi.req.bits.addr, 0x44)
    poke(dut.io.dmi.req.bits.data, 0x00)
    poke(dut.io.dmi.req.valid, 1)
    step(1)

    poke(dut.io.dmi.req.bits.addr, 0x00)
    poke(dut.io.dmi.req.bits.data, 0x00)

    val (taddr:UInt, tdata:UInt, tcount:Int) = target
    var i = 0 //Loop variable

    for( i <- 1 to tcount) {
        poke(dut.io.dmi.req.valid, 0)
        step(1)
    }

    //Read MEM[sbaddress0] to sbdata0
    poke(dut.io.dmi.req.bits.op, DMConsts.dmi_OP_WRITE)
    poke(dut.io.dmi.req.bits.addr, DMI_RegAddrs.DMI_SBADDRESS0)
    poke(dut.io.dmi.req.bits.data, taddr)
    poke(dut.io.dmi.req.valid, 1)
    step(1)

    //Read data to dmi interface
    poke(dut.io.dmi.req.bits.op, DMConsts.dmi_OP_READ)
    poke(dut.io.dmi.req.bits.addr, DMI_RegAddrs.DMI_SBDATA0)
    poke(dut.io.dmi.req.bits.data, 0x00)
    poke(dut.io.dmi.req.valid, 1)
    step(1)

    poke(dut.io.dmi.req.valid, 0)

    while(peek(dut.io.dmi.resp.valid) == BigInt(0)) { step(1) }

    //expect(dut.io.dmi.resp.valid, 1)
    expect(dut.io.dmi.resp.bits.data, tdata)

    step(1)

    poke(dut.io.dmi.req.bits.op, DMConsts.dmi_OP_WRITE)
    poke(dut.io.dmi.req.bits.addr, 0x48)
    poke(dut.io.dmi.req.bits.data, 0x00)
    poke(dut.io.dmi.req.valid, 1)
    step(1)

    poke(dut.io.dmi.req.bits.op, DMConsts.dmi_OP_NONE)
    poke(dut.io.dmi.req.bits.addr, 0x00)
    poke(dut.io.dmi.req.bits.data, 0x00)
    poke(dut.io.dmi.req.valid, 0)
    step(1)
    
}

class RVDMIPeekPokeSpec extends ChiselFlatSpec with Matchers {
  
    it should "Test1: RV Tile should be elaborate normally" in {
        elaborate { 
            implicit val sodor_conf = SodorConfiguration()
            new SodorTile
        }
        info("elaborate SodorTile done")
    }

    it should "Test2: RV DMI Tester return the correct result" in {
        val manager = new TesterOptionsManager {
            testerOptions = testerOptions.copy(backendName = "verilator")
        }

        val rvdmi_inst = List(
            ("h80000000".U, "h800010B7".U), // LUI x1, 0x80001
            ("h80000004".U, "h0000A103".U), // LW x2, x1, 0
            ("h80000008".U, "h00110193".U), // ADDI x3, x2, 1
            ("h8000000C".U, "h0030A023".U), // SW x3, x1, 0
            ("h80000010".U, "h00000033".U), // ADD x0, x0, x0, replace NOP for distinguish pending instruction
            ("h80000014".U, "hFE000EE3".U), // Branch to previous address, here is 0x80000014-4

            ("h80001000".U, "h87654320".U)
        )

        //val rvdmi_target = ("h80001000".U, "h87654321".U, rvdmi_inst.length)

        val rvdmi_target = ("h80001000".U, "h87654321".U, 100)

        info("Expect MEM[0x%08X]:0x%08X".format(rvdmi_target._1.litValue(), rvdmi_target._2.litValue()))

        try {
            implicit val sodor_conf = SodorConfiguration()
            chisel3.iotesters.Driver.execute(() => new SodorTile, manager) {
                dut => new RVDMIPeekPokeTester(dut, rvdmi_inst, rvdmi_target)
            } should be (true)
        } catch {
            case tfe: TestFailedException => {
                info("Failed dmi unit-test")
                throw tfe
            }
        }
        info("Passed dmi unit-test")
    }
}