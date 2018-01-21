To build the bitstream refer to [README](fpga/README.md)
```bash
# currently for some unknown reason gtkterm needs to 
# kept open in background for the isa and bmarks to pass
# in the first go 
$ cd emulator/fpgaartix
$ make dtmglip
# sample to show how to use dtmglip
# +p is the device file name e.g. /dev/ttyUSB1
$ ./dtmxsdb +p<PORT> +loadmem=<RISC-V ELF>
```
To try the automated tests 
```bash
make -i port=<filename> artix-asm-tests
make -i port=<filename> artix-bmarks-test
make -i port=<filename> artix-run #to run all bmark and asm tests
```
