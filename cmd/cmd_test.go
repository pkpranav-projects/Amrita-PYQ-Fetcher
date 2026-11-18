package cmd

import (
	"testing"

	"github.com/spf13/cobra"
)

func TestRoot(t *testing.T) {

	// Test RootCmd
	if _, ok := interface{}(Cmd).(*cobra.Command); !ok {
		t.Errorf("RootCmd is not of type *cobra.Command")
	}

	if Cmd.Use != "amrita-pyq" {
		t.Errorf("Expected Use: 'amrita-pyq', Got: %v", Cmd.Use)
	}

	if Cmd.Short != "AMRITA PYQ FETCHER" {
		t.Errorf("Expected Short: 'AMRITA PYQ FETCHER', Got: %v", Cmd.Short)
	}
}
