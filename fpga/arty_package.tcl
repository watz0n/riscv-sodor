set root "."
set design sodor_artix
set partname "xc7a35ticsg324-1L"
set projdir ./arty_ip_repo/sodor_artix_1.0
set hdl_files [glob $root/arty_hdl/* $root/../vsrc/plusarg_reader.v]
puts $hdl_files
create_project -force $design $projdir -part $partname
set_property target_language Verilog [current_project]

if {[string equal [get_filesets -quiet sources_1] ""]} {
    create_fileset -srcset sources_1
}

add_files -force -verbose -norecurse -copy_to $projdir/src -fileset [get_filesets sources_1] $hdl_files

set_property top sodor_artix_v1_0 [get_filesets sources_1]

ipx::package_project -import_files -force -root_dir $projdir
ipx::associate_bus_interfaces -busif m00_axi -clock m00_axi_aclk [ipx::current_core]

set_property vendor              {user.org}    [ipx::current_core]
set_property library             {user}                  [ipx::current_core]
set_property name             {sodor_artix}                  [ipx::current_core]
set_property taxonomy            {{/AXI_Infrastructure}} [ipx::current_core]
set_property vendor_display_name {user}              [ipx::current_core]
set_property supported_families  { \
                     {artix7}     {Production} \
                     {artix7l}    {Production} \
                     {aartix7}    {Production} \
                     {zynq}       {Production} \
                     {azynq}      {Production} \
                     }   [ipx::current_core]

############################
# Save and Write ZIP archive
############################

ipx::create_xgui_files [ipx::current_core]
ipx::update_checksums [ipx::current_core]
ipx::save_core [ipx::current_core]
close_project
