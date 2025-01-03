import argparse


def enum_parser(enum_type):
    def parse_enum(name):
        try:
            return enum_type[name]
        except ValueError:
            valid_names = [e.name for e in enum_type]
            raise argparse.ArgumentTypeError(f"Invalid choice: {name}. Must be one of {valid_names}.")

    return parse_enum
