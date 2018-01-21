To build the bitstream refer to [README](fpga/README.md)
Open xsdb console
```bash
$ xsdb
xsdb% connect
xsdb% targets 2
xsdb% source ps7_init.tcl
xsdb% ps7_init
xsdb% ps7_post_config
# jumper JP4 should be on jtag
xsdb% fpga -f <BITSTREAM>
xsdb% xsdbserver start
#port number for server will be printed
```
On terminal
```bash
$ cd emulator/fpgazynq
$ make dtmxsdb
# sample to show how to use dtmxsdb
# +p is the port number that xsdb will print
# after starting the xsdbserver
$ ./dtmxsdb +verbose +p<PORT> +loadmem=<RISC-V ELF>
```
To try the automated tests 
```bash
make -i port=<xsdbserver> zynq-asm-tests
make -i port=<xsdbserver> zynq-bmarks-test
make -i port=<xsdbserver> zynq-run #to run all bmark and asm tests
```
