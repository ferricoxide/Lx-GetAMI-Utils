#!/bin/sh
#
# Script to build the downloaded SRPMs
#
#################################################################

function PrepBuildDirs() {
   # Ensure RPM build-tree exists
   test -d ${HOME}/rpmbuild || mkdir -p \
         ${HOME}/rpmbuild/{BUILD,RPMS,SOURCES,SPECS,SRPMS} > /dev/null 2>&1

   # Ensure RPM macro-definition file exists
   test -f ${HOME}/.rpmmacros || echo '%_topdir %(echo $HOME)/rpmbuild' \
      > ${HOME}/.rpmmacros
}

function ExtractSource() {
  local EXTDIR="/tmp/srpm_extract"
  test -d ${EXTDIR} || mkdir -p ${EXTDIR}
  for SRPM in *.src.rpm
  do
     rpm2cpio ${SRPM} | ( cd ${EXTDIR} ; cpio -idv )
  done
}

PrepBuildDirs
ExtractSource
