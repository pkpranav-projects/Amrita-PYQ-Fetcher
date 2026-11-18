package cmd

import (
	"github.com/spf13/cobra"
)

func init() {
	Cmd.AddCommand(versionCmd)
}

var versionCmd = &cobra.Command{
	Use:   "version",
	Short: "Print the version number of amrita-pyq",
	Long:  `Displays version of AMRITA PYQ FETCHER installed on the system.`,
	Run: func(cmd *cobra.Command, args []string) {
		cmd.Println("AMRITA PYQ FETCHER v2.0.0")
	},
}
